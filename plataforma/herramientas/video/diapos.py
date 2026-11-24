"""Las diapositivas del vídeo de la demo en AWS (montar.sh). Uso: python3 diapos.py <carpeta con las capturas>"""
import sys
from PIL import Image, ImageDraw, ImageFont
D=sys.argv[1]; W,H=1920,1080
BG=(19,18,17); TX=(238,235,229); MU=(163,159,152); AC=(242,140,122)
F='/usr/share/fonts/opentype/inter/Inter-Regular.otf'; FB='/usr/share/fonts/opentype/inter/Inter-Bold.otf'
f=lambda s,b=False: ImageFont.truetype(FB if b else F, s)
def base(): return Image.new('RGB',(W,H),BG)
def textos(im, eyebrow, titulo, sub=None, y=90):
    d=ImageDraw.Draw(im); d.text((110,y),eyebrow.upper(),font=f(28,True),fill=AC)
    d.text((110,y+50),titulo,font=f(52,True),fill=TX)
    if sub: d.text((110,y+125),sub,font=f(30),fill=MU)
def captura(nombre, eyebrow, titulo, sub, salida):
    im=base(); textos(im,eyebrow,titulo,sub)
    c=Image.open(f'{D}/{nombre}').convert('RGB'); top=300; mw,mh=W-220,H-top-70
    r=min(mw/c.width, mh/c.height); c=c.resize((int(c.width*r),int(c.height*r)),Image.LANCZOS)
    im.paste(c,((W-c.width)//2, top+(mh-c.height)//2)); im.save(f'{D}/{salida}')
# portada
im=base(); d=ImageDraw.Draw(im)
d.text((110,300),'RADAR DE SUBVENCIONES · PLATAFORMA',font=f(30,True),fill=AC)
d.text((110,360),'De una web estática a la arquitectura',font=f(68,True),fill=TX)
d.text((110,450),'de una empresa, desplegada en AWS',font=f(68,True),fill=TX)
d.text((110,580),'Spring Boot hexagonal · Kafka · PostgreSQL · Kubernetes (EKS) · Terraform · OpenTelemetry · OAuth2',font=f(30),fill=MU)
d.text((110,900),'Félix Corcoran · felixcorcoran.es',font=f(30),fill=MU); im.save(f'{D}/00.png')
# arquitectura
im=base(); textos(im,'Cómo encaja todo','Cinco servicios unidos por Kafka','Cada uno con su PostgreSQL; outbox al publicar, idempotencia y cola de errores al consumir')
d=ImageDraw.Draw(im)
cajas=[('BDNS',150,520),('recogida',420,520),('lectura\n(Gemini)',740,520),('encaje',1100,380),('avisos',1100,560),('preguntas\n(pgvector)',1100,740),('pasarela\n+ Keycloak',1480,560)]
for t,x,y in cajas:
    d.rounded_rectangle((x,y,x+250,y+120),radius=22,outline=AC if t!='BDNS' else MU,width=4)
    d.multiline_text((x+125,y+60),t,font=f(32,True),fill=TX,anchor='mm',align='center')
for a,b in [((400,580),(420,580)),((670,580),(740,580)),((990,580),(1100,440)),((990,580),(1100,620)),((990,580),(1100,800)),((1350,620),(1480,620))]:
    d.line((a,b),fill=MU,width=4)
d.text((690,470),'Kafka',font=f(28),fill=AC); d.text((110,950),'Todo en un chart de Helm: los mismos manifiestos en kind (local) y en EKS.',font=f(30),fill=MU)
im.save(f'{D}/01.png')
captura('aws-terminal.png','1 · Desplegado','Dos nodos en EKS y los diez pods en marcha','kubectl y la AWS CLI: clúster activo y PostgreSQL 17 en RDS','02.png')
captura('aws-consola-eks.png','2 · Amazon EKS','El clúster «subvenciones», activo','Kubernetes 1.35 en eu-west-1 · 16 pods · 2 nodos · 0 problemas','03.png')
captura('aws-consola-rds.png','3 · Amazon RDS','PostgreSQL gestionado','Una base por servicio, creada al arrancar; sin copias: es una demo','04.png')
captura('aws-consola-ecr.png','4 · Amazon ECR','Una imagen por servicio','Construidas con Docker y subidas con la AWS CLI','05.png')
# grafana: alto, se hará panorámica en ffmpeg; aquí solo el marco del título
im=base(); textos(im,'5 · Observabilidad','OpenTelemetry y Grafana, con tráfico real','Trazas que cruzan Kafka y la outbox, métricas y alertas por correo'); im.save(f'{D}/06-marco.png')
im=base(); d=ImageDraw.Draw(im)
d.text((110,300),'Levantado, probado y apagado',font=f(64,True),fill=TX)
d.text((110,400),'Todo con Terraform: red, EKS, RDS y ECR. En producción costaría',font=f(34),fill=MU)
d.text((110,450),'decenas de euros al mes, así que la demo se graba y se borra:',font=f(34),fill=MU)
d.rounded_rectangle((110,540,1000,620),radius=14,fill=(38,35,33)); d.text((140,560),'$ terraform destroy',font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',38),fill=AC)
d.text((110,700),'La web real sigue a coste cero: subvenciones.felixcorcoran.es',font=f(34),fill=TX)
d.text((110,760),'Código: github.com/felixcorcoran/subvenciones/tree/main/plataforma',font=f(30),fill=MU)
im.save(f'{D}/07.png')
