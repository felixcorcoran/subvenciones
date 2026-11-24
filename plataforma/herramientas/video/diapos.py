"""Las diapositivas del vídeo de la demo en AWS (montar.sh), con la estética de
dev.felixcorcoran.es: fondo negro, azul #7a95ff, Archivo ancha en mayúsculas,
serif cursiva de acento y monoespaciada en los rótulos.
Uso: python3 diapos.py <carpeta con las capturas y rds/ecr-compacta.png> [es|en]"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

D = sys.argv[1]
EN = len(sys.argv) > 2 and sys.argv[2] == 'en'
T = lambda es, en: en if EN else es
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
rotulo(d, 110, 250, T('Radar de subvenciones · plataforma', 'Grants radar · platform'))
d.text((106, 300), T('DE UNA WEB ESTÁTICA', 'FROM A STATIC WEBSITE'), font=archivo(108, 900), fill=TX)
d.text((110, 430), T('a una plataforma en AWS.', 'to a platform on AWS.'), font=serif(130), fill=AC)
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
rotulo(d, 110, 86, T('Cómo encaja todo', 'How it fits together'))
d.text((110, 128), T('CINCO SERVICIOS UNIDOS POR KAFKA', 'FIVE SERVICES LINKED BY KAFKA'), font=archivo(56), fill=TX)
d.text((110, 206), T('Cada uno con su PostgreSQL; outbox al publicar, idempotencia y cola de errores al consumir', 'Each with its own PostgreSQL; outbox when publishing, idempotency and a dead-letter queue when consuming'), font=archivo(30, 400, 100), fill=MU)
cajas = [('BDNS', 150, 520, False), (T('recogida', 'collection'), 420, 520, True), (T('lectura\n(Gemini)', 'reading\n(Gemini)'), 740, 520, True),
         (T('encaje', 'matching'), 1100, 380, True), (T('avisos', 'alerts'), 1100, 560, True), (T('preguntas\n(pgvector)', 'questions\n(pgvector)'), 1100, 740, True),
         (T('pasarela\n+ Keycloak', 'gateway\n+ Keycloak'), 1480, 560, True)]
for a, b in [((400, 580), (420, 580)), ((670, 580), (740, 580)), ((990, 580), (1100, 440)), ((990, 580), (1100, 620)),
             ((990, 580), (1100, 800)), ((1350, 620), (1480, 620))]:
    d.line((a, b), fill=(70, 72, 84), width=3)
for t, x, y, servicio in cajas:
    d.rounded_rectangle((x, y, x + 250, y + 120), radius=20, fill=PANEL, outline=AC if servicio else (70, 72, 84), width=3)
    d.multiline_text((x + 125, y + 60), t, font=archivo(30, 700, 110), fill=TX, anchor='mm', align='center')
d.text((700, 470), 'Kafka', font=mono(26), fill=AC)
d.text((110, 950), T('Todo en un chart de Helm: los mismos manifiestos en kind (local) y en EKS.', 'All in one Helm chart: the same manifests on kind (local) and on EKS.'), font=archivo(30, 400, 100), fill=MU)
im.save(f'{D}/01.png')

captura('aws-terminal.png', 1, T('Desplegado', 'Deployed'), T('Dos nodos en EKS y diez pods en marcha', 'Two EKS nodes and ten pods running'), T('kubectl y la AWS CLI: clúster activo y PostgreSQL 17 en RDS', 'kubectl and the AWS CLI: active cluster and PostgreSQL 17 on RDS'), '02.png')
captura('aws-consola-eks.png', 2, 'Amazon EKS', T('El clúster «subvenciones», activo', 'The "subvenciones" cluster, active'), T('Kubernetes 1.35 en eu-west-1 · 16 pods · 2 nodos · 0 problemas', 'Kubernetes 1.35 in eu-west-1 · 16 pods · 2 nodes · 0 issues'), '03.png')
captura('rds-compacta.png', 3, 'Amazon RDS', T('PostgreSQL gestionado', 'Managed PostgreSQL'), T('Una base por servicio, creada al arrancar; sin copias: es una demo', 'One database per service, created on startup; no backups: it is a demo'), '04.png')
captura('ecr-compacta.png', 4, 'Amazon ECR', T('Una imagen por servicio', 'One image per service'), T('Construidas con Docker y subidas con la AWS CLI', 'Built with Docker and pushed with the AWS CLI'), '05.png')

# Grafana: el marco; la captura se desplaza por debajo en montar.sh
im = base(); cabecera(im, 5, T('Observabilidad', 'Observability'), T('OpenTelemetry y Grafana, con tráfico', 'OpenTelemetry and Grafana, live'), T('Trazas que cruzan Kafka y la outbox, métricas y alertas por correo', 'Traces across Kafka and the outbox, metrics and email alerts'))
im.save(f'{D}/06-marco.png')

# Cierre
im = base(); d = ImageDraw.Draw(im)
rotulo(d, 110, 250, T('Coste', 'Cost'))
d.text((106, 300), T('LEVANTADO, PROBADO', 'DEPLOYED, TESTED'), font=archivo(100, 900), fill=TX)
d.text((110, 420), T('y apagado.', 'and torn down.'), font=serif(130), fill=AC)
d.text((110, 600), T('Todo con Terraform: red, EKS, RDS y ECR. Encendido cuesta dinero cada hora,', 'All with Terraform: network, EKS, RDS and ECR. Running it costs money every hour,'), font=archivo(32, 400, 100), fill=MU)
d.text((110, 648), T('así que la demo se graba y se borra:', 'so the demo is recorded and then destroyed:'), font=archivo(32, 400, 100), fill=MU)
d.rounded_rectangle((110, 720, 760, 800), radius=16, fill=PANEL, outline=(40, 41, 48), width=2)
d.text((140, 740), '$ terraform destroy', font=mono(36), fill=AC)
d.text((110, 900), T('La web real sigue a coste cero: subvenciones.felixcorcoran.es', 'The live website still runs at zero cost: subvenciones.felixcorcoran.es'), font=archivo(30, 500, 100), fill=TX)
d.text((110, 950), 'github.com/felixcorcoran/subvenciones/tree/main/plataforma', font=mono(26), fill=FA)
im.save(f'{D}/07.png')
