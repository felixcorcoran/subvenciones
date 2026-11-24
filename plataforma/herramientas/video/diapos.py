"""Las diapositivas del vídeo de la demo en AWS (montar.sh), con la estética de
dev.felixcorcoran.es: fondo negro, azul #7a95ff, Archivo ancha en mayúsculas,
serif cursiva de acento y monoespaciada en los rótulos.
Uso: python3 diapos.py <carpeta con las capturas y rds/ecr-compacta.png>"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

D = sys.argv[1]
F = Path(__file__).parent / 'fuentes'
W, H = 1920, 1080
BG, PANEL = (10, 10, 11), (21, 21, 26)
TX, MU, FA, AC = (241, 239, 234), (164, 165, 173), (129, 130, 139), (122, 149, 255)


def archivo(tam, peso=800, ancho=125):
    f = ImageFont.truetype(str(F / 'archivo.ttf'), tam)
    f.set_variation_by_axes([peso, ancho])
    return f


serif = lambda tam: ImageFont.truetype(str(F / 'serif-it.ttf'), tam)


def mono(tam, peso=500):
    f = ImageFont.truetype(str(F / 'mono.ttf'), tam)
    f.set_variation_by_axes([peso])
    return f


def base():
    return Image.new('RGB', (W, H), BG)


def rotulo(d, x, y, texto):
    """«—— TEXTO» en monoespaciada, como los rótulos de sección del portfolio"""
    d.line((x, y + 14, x + 44, y + 14), fill=AC, width=2)
    d.text((x + 64, y), texto.upper(), font=mono(24), fill=MU)


def cabecera(im, num, rot, titulo, sub):
    d = ImageDraw.Draw(im)
    rotulo(d, 110, 86, rot)
    d.text((W - 110, 86), f'{num:02d} / 05', font=mono(24), fill=FA, anchor='ra')
    d.text((110, 128), titulo.upper(), font=archivo(56), fill=TX)
    d.text((110, 206), sub, font=archivo(30, 400, 100), fill=MU)


def captura(nombre, num, rot, titulo, sub, salida):
    im = base()
    cabecera(im, num, rot, titulo, sub)
    c = Image.open(f'{D}/{nombre}').convert('RGB')
    top, mw, mh = 290, W - 220, H - 290 - 70
    r = min(mw / c.width, mh / c.height)
    c = c.resize((int(c.width * r), int(c.height * r)), Image.LANCZOS)
    im.paste(c, ((W - c.width) // 2, top + (mh - c.height) // 2))
    im.save(f'{D}/{salida}')


# Portada
im = base(); d = ImageDraw.Draw(im)
rotulo(d, 110, 250, 'Radar de subvenciones · plataforma')
d.text((106, 300), 'DE UNA WEB ESTÁTICA', font=archivo(108, 900), fill=TX)
d.text((110, 430), 'a una plataforma en AWS.', font=serif(130), fill=AC)
x = 110
for chip in ['Spring Boot', 'Kafka', 'Kubernetes (EKS)', 'Terraform', 'OpenTelemetry', 'OAuth2']:
    w = d.textlength(chip, font=mono(26)) + 44
    d.rounded_rectangle((x, 640, x + w, 694), radius=27, outline=(60, 61, 70), width=2)
    d.text((x + 22, 654), chip, font=mono(26), fill=TX)
    x += w + 14
d.text((110, 940), 'Félix Corcoran · dev.felixcorcoran.es', font=mono(26), fill=FA)
im.save(f'{D}/00.png')

# Arquitectura
im = base(); d = ImageDraw.Draw(im)
rotulo(d, 110, 86, 'Cómo encaja todo')
d.text((110, 128), 'CINCO SERVICIOS UNIDOS POR KAFKA', font=archivo(56), fill=TX)
d.text((110, 206), 'Cada uno con su PostgreSQL; outbox al publicar, idempotencia y cola de errores al consumir', font=archivo(30, 400, 100), fill=MU)
cajas = [('BDNS', 150, 520, False), ('recogida', 420, 520, True), ('lectura\n(Gemini)', 740, 520, True),
         ('encaje', 1100, 380, True), ('avisos', 1100, 560, True), ('preguntas\n(pgvector)', 1100, 740, True),
         ('pasarela\n+ Keycloak', 1480, 560, True)]
for a, b in [((400, 580), (420, 580)), ((670, 580), (740, 580)), ((990, 580), (1100, 440)), ((990, 580), (1100, 620)),
             ((990, 580), (1100, 800)), ((1350, 620), (1480, 620))]:
    d.line((a, b), fill=(70, 72, 84), width=3)
for t, x, y, servicio in cajas:
    d.rounded_rectangle((x, y, x + 250, y + 120), radius=20, fill=PANEL, outline=AC if servicio else (70, 72, 84), width=3)
    d.multiline_text((x + 125, y + 60), t, font=archivo(30, 700, 110), fill=TX, anchor='mm', align='center')
d.text((700, 470), 'Kafka', font=mono(26), fill=AC)
d.text((110, 950), 'Todo en un chart de Helm: los mismos manifiestos en kind (local) y en EKS.', font=archivo(30, 400, 100), fill=MU)
im.save(f'{D}/01.png')

captura('aws-terminal.png', 1, 'Desplegado', 'Dos nodos en EKS y diez pods en marcha', 'kubectl y la AWS CLI: clúster activo y PostgreSQL 17 en RDS', '02.png')
captura('aws-consola-eks.png', 2, 'Amazon EKS', 'El clúster «subvenciones», activo', 'Kubernetes 1.35 en eu-west-1 · 16 pods · 2 nodos · 0 problemas', '03.png')
captura('rds-compacta.png', 3, 'Amazon RDS', 'PostgreSQL gestionado', 'Una base por servicio, creada al arrancar; sin copias: es una demo', '04.png')
captura('ecr-compacta.png', 4, 'Amazon ECR', 'Una imagen por servicio', 'Construidas con Docker y subidas con la AWS CLI', '05.png')

# Grafana: el marco; la captura se desplaza por debajo en montar.sh
im = base(); cabecera(im, 5, 'Observabilidad', 'OpenTelemetry y Grafana, con tráfico', 'Trazas que cruzan Kafka y la outbox, métricas y alertas por correo')
im.save(f'{D}/06-marco.png')

# Cierre
im = base(); d = ImageDraw.Draw(im)
rotulo(d, 110, 250, 'Coste')
d.text((106, 300), 'LEVANTADO, PROBADO', font=archivo(100, 900), fill=TX)
d.text((110, 420), 'y apagado.', font=serif(130), fill=AC)
d.text((110, 600), 'Todo con Terraform: red, EKS, RDS y ECR. Encendido cuesta dinero cada hora,', font=archivo(32, 400, 100), fill=MU)
d.text((110, 648), 'así que la demo se graba y se borra:', font=archivo(32, 400, 100), fill=MU)
d.rounded_rectangle((110, 720, 760, 800), radius=16, fill=PANEL, outline=(40, 41, 48), width=2)
d.text((140, 740), '$ terraform destroy', font=mono(36), fill=AC)
d.text((110, 900), 'La web real sigue a coste cero: subvenciones.felixcorcoran.es', font=archivo(30, 500, 100), fill=TX)
d.text((110, 950), 'github.com/felixcorcoran/subvenciones/tree/main/plataforma', font=mono(26), fill=FA)
im.save(f'{D}/07.png')
