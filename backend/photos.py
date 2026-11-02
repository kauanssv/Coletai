"""Bounded JPEG uploads; strip optional metadata without decoding untrusted pixels."""
MAX_BYTES = 256 * 1024


def clean_jpeg(data):
    if not data or len(data) > MAX_BYTES or not data.startswith(b"\xff\xd8") or not data.endswith(b"\xff\xd9"):
        raise ValueError("Envie uma foto JPEG de até 256 KB")
    result = bytearray(data[:2])
    offset = 2
    dimensions = False
    while offset < len(data) - 2:
        start = offset
        if data[offset] != 0xff:
            raise ValueError("Foto JPEG inválida")
        while offset < len(data) and data[offset] == 0xff:
            offset += 1
        if offset >= len(data):
            break
        marker = data[offset]
        offset += 1
        if marker in (0x00, 0xd8, 0xd9) or offset + 2 > len(data):
            raise ValueError("Foto JPEG inválida")
        length = int.from_bytes(data[offset:offset + 2], "big")
        end = offset + length
        if length < 2 or end > len(data):
            raise ValueError("Foto JPEG inválida")
        if marker in (0xc0, 0xc1, 0xc2):
            if length < 8:
                raise ValueError("Foto JPEG inválida")
            height = int.from_bytes(data[offset + 3:offset + 5], "big")
            width = int.from_bytes(data[offset + 5:offset + 7], "big")
            if not 0 < width <= 1600 or not 0 < height <= 1600:
                raise ValueError("A foto deve ter até 1600 pixels por lado")
            dimensions = True
        if marker == 0xda:
            if not dimensions:
                raise ValueError("Foto JPEG inválida")
            result.extend(data[start:])
            return bytes(result)
        if marker not in range(0xe1, 0xf0) and marker != 0xfe:
            result.extend(data[start:end])
        offset = end
    raise ValueError("Foto JPEG inválida")
