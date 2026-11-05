import unittest
import test_api


class AccountTests(unittest.TestCase):
    setUp = test_api.ApiTests.setUp
    tearDown = test_api.ApiTests.tearDown
    headers = test_api.ApiTests.headers
    state = test_api.ApiTests.state
    perform = test_api.ApiTests.perform
    upload = test_api.ApiTests.upload
    order = test_api.ApiTests.order
    transition = test_api.ApiTests.transition

    def own(self, email):
        return next(p for p in self.state(email)["profiles"] if p["email"] == email)

    def test_password_mismatch_wrong_password_and_invalid_data_are_atomic(self):
        email = "c1@test.com"
        before = self.own(email)
        photo = self.upload(email)
        payload = dict(before, name="Novo nome", document="12345678901", profile_photo=photo,
                       current_password="senha-segura", password="senha-nova", password_confirmation="diferente")
        self.perform(email, "profile.account", payload, 400)
        self.assertEqual(before, self.own(email))
        payload.update(password_confirmation="senha-nova", current_password="incorreta")
        self.perform(email, "profile.account", payload, 403)
        self.assertEqual(before, self.own(email))
        payload.update(current_password="senha-segura", email="email-invalido")
        self.perform(email, "profile.account", payload, 400)
        self.assertEqual(before, self.own(email))
        self.assertEqual(200, self.client.post("/v1/login", json=dict(email=email, password="senha-segura")).status_code)
        self.assertEqual(401, self.client.post("/v1/login", json=dict(email=email, password="senha-nova")).status_code)

    def test_all_roles_update_account_and_photo_with_optional_password(self):
        company = dict(role="empresa", name="Empresa", email="empresa@test.com", password="senha-segura",
                       company="Reciclagem", document="12345678000190", description="Cooperativa")
        self.assertEqual(201, self.client.post("/v1/register", json=company).status_code)
        self.tokens[company["email"]] = self.client.post("/v1/login", json=company).json()["token"]
        for email in ("c1@test.com", "r1@test.com", "empresa@test.com", "admin@test.com"):
            profile = self.own(email)
            photo = self.upload(email)
            profile.update(name="Nome atualizado", phone="11999999999", profile_photo=photo,
                           document="12345678000190" if profile["role"] == "empresa" else "12345678901")
            profile.update(current_password="senha-segura", password="senha-nova", password_confirmation="senha-nova")
            self.perform(email, "profile.account", profile)
            updated = self.own(email)
            self.assertEqual("Nome atualizado", updated["name"])
            self.assertEqual(photo, updated["profile_photo"])
            self.assertEqual(200, self.client.get("/v1/photos/" + photo["id"], headers=self.headers(email)).status_code)
            self.assertEqual(401, self.client.post("/v1/login", json=dict(email=email, password="senha-segura")).status_code)
            self.assertEqual(200, self.client.post("/v1/login", json=dict(email=email, password="senha-nova")).status_code)
            self.perform(email, "profile.account", dict(updated, profile_photo=None))
            self.assertIsNone(self.own(email)["profile_photo"])

    def test_rejects_other_accounts_photos_and_stale_revisions(self):
        own = self.own("c1@test.com")
        own["document"] = "12345678901"
        foreign = self.upload("c2@test.com")
        self.perform("c1@test.com", "profile.account", dict(own, profile_photo=foreign), 403)
        self.perform("c2@test.com", "profile.account", own, 403)
        self.perform("admin@test.com", "profile.account", own, 403)
        self.perform("c1@test.com", "profile.account", own)
        self.perform("c1@test.com", "profile.account", own, 409)

    def test_citizen_sees_and_downloads_current_collector_photo_only_after_approval(self):
        collector = "r1@test.com"
        photo = self.upload(collector)
        self.perform(collector, "profile.account", dict(self.own(collector), document="12345678901", profile_photo=photo))
        req = self.order()
        req = self.transition(collector, req, "accept")
        req = self.perform(collector, "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="25", message=""))["requests"][0]
        self.assertIsNone(req["responsible_photo"])
        self.assertEqual(403, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("c1@test.com")).status_code)
        req = self.transition("c1@test.com", req, "approve")
        for step in (None, "going", "arrive", "collect", "complete"):
            if step: req = self.transition(collector, req, step)
            citizen_order = self.state("c1@test.com")["requests"][0]
            self.assertEqual(photo, citizen_order["responsible_photo"])
            self.assertEqual(200, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("c1@test.com")).status_code)
            self.assertEqual(403, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("c2@test.com")).status_code)
        replacement = self.upload(collector)
        self.perform(collector, "profile.account", dict(self.own(collector), profile_photo=replacement))
        self.assertEqual(replacement, self.state("c1@test.com")["requests"][0]["responsible_photo"])
        self.assertEqual(403, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("c1@test.com")).status_code)
        self.perform(collector, "profile.account", dict(self.own(collector), profile_photo=None))
        self.assertIsNone(self.state("c1@test.com")["requests"][0]["responsible_photo"])

    def test_self_deletion_preserves_data_admin_restores_and_old_tokens_stay_revoked(self):
        email = "c1@test.com"
        order = self.order()
        before = self.own(email)
        login = self.client.post("/v1/login", json=dict(email=email, password="senha-segura"))
        second_token = login.json()["token"]
        response = self.client.post("/v1/action", headers=self.headers(email), json=dict(action="profile.delete", payload=dict(id=before["id"], revision=before["revision"])))
        self.assertEqual(200, response.status_code, response.text)
        self.assertEqual({"ok": True}, response.json())
        self.assertEqual(401, self.client.get("/v1/state", headers=self.headers(email)).status_code)
        self.assertEqual(401, self.client.get("/v1/state", headers={"Authorization": "Bearer " + second_token}).status_code)
        self.assertEqual(401, self.client.post("/v1/login", json=dict(email=email, password="senha-segura")).status_code)
        admin = self.state("admin@test.com")
        deleted = next(profile for profile in admin["profiles"] if profile["id"] == before["id"])
        self.assertEqual("deleted", deleted["status"])
        self.assertEqual(before["name"], deleted["name"])
        self.assertTrue(any(req["id"] == order["id"] for req in admin["requests"]))
        self.perform("admin@test.com", "profile.status", dict(id=before["id"], status="active"))
        self.assertEqual(401, self.client.get("/v1/state", headers=self.headers(email)).status_code)
        login = self.client.post("/v1/login", json=dict(email=email, password="senha-segura"))
        self.assertEqual(200, login.status_code)
        self.tokens[email] = login.json()["token"]
        self.assertEqual(order["id"], self.state(email)["requests"][0]["id"])

    def test_self_deletion_rejects_other_accounts_stale_revision_and_last_admin(self):
        own = self.own("c1@test.com")
        self.perform("c2@test.com", "profile.delete", dict(id=own["id"], revision=own["revision"]), 403)
        self.perform("admin@test.com", "profile.delete", dict(id=own["id"], revision=own["revision"]), 403)
        self.perform("c1@test.com", "profile.delete", dict(id=own["id"], revision=own["revision"] - 1), 409)
        admin = self.own("admin@test.com")
        self.perform("admin@test.com", "profile.delete", dict(id=admin["id"], revision=admin["revision"]), 400)
        self.assertEqual("active", self.own("c1@test.com")["status"])
