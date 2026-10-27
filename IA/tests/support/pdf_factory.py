from pathlib import Path


def write_text_pdf(path: Path, pages: list[str]) -> Path:
    """PDF mínimo con una línea de texto por página, para probar la lectura sin archivos reales."""
    objects = ["<< /Type /Catalog /Pages 2 0 R >>", ""]
    page_ids = []
    for text in pages:
        content = f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET".encode("latin-1")
        content_id = len(objects) + 1
        objects.append(f"<< /Length {len(content)} >>\nstream\n{content.decode('latin-1')}\nendstream")
        page_ids.append(len(objects) + 1)
        objects.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents {content_id} 0 R "
                       f"/Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>")
    objects[1] = f"<< /Type /Pages /Kids [{' '.join(f'{i} 0 R' for i in page_ids)}] /Count {len(page_ids)} >>"

    output = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(output))
        output += f"{number} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(output)
    output += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("latin-1")
    output += "".join(f"{offset:010d} 00000 n \n" for offset in offsets).encode("latin-1")
    output += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode("latin-1")
    path.write_bytes(bytes(output))
    return path
