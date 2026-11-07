"""Regressões QA: perfis simultâneos, dados inválidos e persistência."""
import unittest
import test_api


class CrossProfileTests(unittest.TestCase):
    setUp = test_api.ApiTests.setUp
    tearDown = test_api.ApiTests.tearDown
    headers = test_api.ApiTests.headers
    state = test_api.ApiTests.state
    perform = test_api.ApiTests.perform
    order = test_api.ApiTests.order
    transition = test_api.ApiTests.transition

    def company(self):
        payload = dict(role="empresa", name="Responsável QA", email="empresa@test.com",
                       password="senha-segura", company="Reciclagem QA",
                       document="12345678000190", description="Cooperativa de teste")
        response = self.client.post("/v1/register", json=payload)
        self.assertEqual(201, response.status_code, response.text)
        response = self.client.post("/v1/login", json=dict(email=payload["email"], password=payload["password"]))
        self.assertEqual(200, response.status_code, response.text)
        self.tokens[payload["email"]] = response.json()["token"]
        return payload["email"]

    def test_empresa_concorre_com_ambulante_conclui_e_recebe_avaliacao(self):
        company = self.company()
        req = self.order()
        self.assertEqual(req["id"], self.state(company)["requests"][0]["id"])
        assigned = self.transition(company, req, "accept")
        self.perform("r1@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="accept"), 403)
        assigned = self.perform(company, "request.transition", dict(id=req["id"], revision=assigned["revision"], action="propose", price="35,50", message="Proposta da cooperativa"))["requests"][0]
        self.assertNotIn("street", assigned["address"])
        self.assertEqual("Reciclagem QA", assigned["responsible_name"])
        self.perform("c2@test.com", "request.transition", dict(id=req["id"], revision=assigned["revision"], action="approve"), 403)
        assigned = self.transition("c1@test.com", assigned, "approve")
        for step in ("going", "arrive", "collect", "complete"):
            assigned = self.transition(company, assigned, step)
        self.perform("c1@test.com", "request.review", dict(id=req["id"], score=4, comment="Cooperativa pontual"))
        profile = self.state(company)["profiles"][0]
        self.assertEqual(1, profile["rating_count"])
        self.assertEqual(4, profile["rating_average"])
        self.assertEqual([], self.state("r2@test.com")["requests"])
        self.assertTrue(self.state("admin@test.com")["audit"])

    def test_empresa_preserva_descricao_no_cadastro_e_edicao(self):
        company = self.company()
        profile = self.state(company)["profiles"][0]
        self.assertEqual("Cooperativa de teste", profile["description"])
        profile["description"] = "Agora recebemos eletrônicos"
        self.perform(company, "profile.save", profile)
        self.assertEqual(profile["description"], self.state(company)["profiles"][0]["description"])

    def test_endereco_rejeita_limites_do_mysql_sem_perder_original(self):
        for field, limit in (("nickname", 50), ("number", 20), ("complement", 100), ("district", 100), ("city", 100)):
            with self.subTest(field=field):
                payload = dict(self.address, **{field: "x" * (limit + 1)})
                self.perform("c1@test.com", "address.save", payload, 400)
                self.assertEqual(self.address[field] if field in self.address else "", self.state("c1@test.com")["addresses"][0].get(field, ""))

    def test_materiais_rejeitam_ids_booleanos_e_fracionarios(self):
        for value in (True, 1.5, 10**30, "9" * 30):
            with self.subTest(value=value):
                self.perform("r1@test.com", "accepted.save", dict(ids=[value]), 400)
                self.assertEqual({"1", "2"}, set(self.state("r1@test.com")["accepted_materials"]))

    def test_perfis_comuns_nao_executam_acoes_administrativas(self):
        company = self.company()
        owner = self.state("c1@test.com")["profiles"][0]
        for email in ("c1@test.com", "r1@test.com", company):
            for action, payload in (("material.save", dict(name="Material invasor")),
                                    ("info.save", dict(name="Alterado")),
                                    ("profile.status", dict(id=owner["id"], status="blocked"))):
                with self.subTest(email=email, action=action):
                    self.perform(email, action, payload, 403)
        self.assertEqual("active", self.state("c1@test.com")["profiles"][0]["status"])

    def test_identificador_de_pedido_nao_pode_ser_booleano_ou_fracionario(self):
        req = self.order()
        for value in (True, 1.5, 10**30):
            with self.subTest(value=value):
                self.perform("c1@test.com", "request.transition", dict(id=value, revision=req["revision"], action="cancel"), 400)
        self.assertEqual("aberta", self.state("c1@test.com")["requests"][0]["status"])

    def test_endereco_rejeita_coordenadas_parciais_sem_apagar_dados(self):
        for values in (dict(latitude=-23.55), dict(longitude=-46.63), dict(latitude=True, longitude=0)):
            with self.subTest(values=values):
                self.perform("c1@test.com", "address.save", dict(self.address, **values), 400)
        self.assertNotIn("latitude", self.state("c1@test.com")["addresses"][0])

    def test_todos_perfis_salvam_documento_e_trocam_senha(self):
        company = self.company()
        for email in ("c1@test.com", "r1@test.com", company):
            with self.subTest(email=email):
                profile = self.state(email)["profiles"][0]
                profile["document"] = "98765432000199" if email == company else "12345678901"
                self.perform(email, "profile.save", profile)
                self.assertEqual(profile["document"], self.state(email)["profiles"][0]["document"])
                self.perform(email, "profile.password", dict(id=profile["id"], current_password="senha-segura", password="nova-senha-qa"))
                self.assertEqual(401, self.client.post("/v1/login", json=dict(email=email, password="senha-segura")).status_code)
                self.assertEqual(200, self.client.post("/v1/login", json=dict(email=email, password="nova-senha-qa")).status_code)

    def test_conta_bloqueada_perde_acesso_e_restaurada_recupera_pedidos(self):
        req = self.order()
        profile = self.state("c1@test.com")["profiles"][0]
        self.perform("admin@test.com", "profile.status", dict(id=profile["id"], status="blocked"))
        self.assertEqual(401, self.client.get("/v1/state", headers=self.headers("c1@test.com")).status_code)
        self.perform("admin@test.com", "profile.status", dict(id=profile["id"], status="active"))
        self.assertEqual(req["id"], self.state("c1@test.com")["requests"][0]["id"])

    def test_proposta_recusada_permite_outra_empresa_e_isola_endereco(self):
        company = self.company()
        req = self.transition("r1@test.com", self.order(), "accept")
        req = self.perform("r1@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="50", message=""))["requests"][0]
        req = self.transition("c1@test.com", req, "decline")
        req = self.transition(company, req, "accept")
        req = self.perform(company, "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="40", message=""))["requests"][0]
        self.transition("c1@test.com", req, "approve")
        self.assertEqual("Rua Um", self.state(company)["requests"][0]["address"]["street"])
        self.assertNotIn("street", self.state("r1@test.com")["requests"][0]["address"])


if __name__ == "__main__":
    unittest.main()
