"""Capturas de RDS y ECR más compactas para el vídeo: se quitan los huecos vacíos
entre columnas juntando franjas verticales de la captura original (y en RDS las
cuatro columnas pasan a dos filas). Todo lo que se ve sale de la captura real."""
import sys
from PIL import Image
D = sys.argv[1]

def franjas(img, y0, y1, trozos):
    """Une franjas [x0, x1) de la banda [y0, y1) de img, de izquierda a derecha"""
    w = sum(b - a for a, b in trozos)
    out = Image.new('RGB', (w, y1 - y0))
    x = 0
    for a, b in trozos:
        out.paste(img.crop((a, y0, b, y1)), (x, 0)); x += b - a
    return out

def apilar(bandas):
    w = max(b.width for b in bandas)
    out = Image.new('RGB', (w, sum(b.height for b in bandas)))
    y = 0
    for b in bandas:
        out.paste(b, (0, y)); y += b.height
    return out

# RDS: arriba (barra, cabecera, «Resumen»), columnas 1-2, columnas 3-4, abajo
rds = Image.open(f'{D}/aws-consola-rds.png').convert('RGB'); W = rds.width
fin = [(W - 470, W)]                       # botones y borde derecho
arriba = franjas(rds, 0, 258, [(0, 1100)] + fin)
fila1 = franjas(rds, 258, 388, [(0, 1100)] + fin)
# Columnas 3-4 alineadas con 1-2: el texto de la 3 en x=90 y su separador bajo el de arriba
# (24 px de relleno vacío de la propia columna 3 antes del separador)
fila2 = franjas(rds, 258, 388, [(0, 84), (1686, 2420), (2300, 2324), (2420, 2678)] + fin)
abajo = franjas(rds, 388, rds.height, [(0, 1100)] + fin)
apilar([arriba, fila1, fila2, abajo]).save(f'{D}/rds-compacta.png')

# ECR: cabecera (botones a la derecha, relleno neutro en medio) y tabla con las columnas juntas
ecr = Image.open(f'{D}/aws-consola-ecr.png').convert('RGB'); W = ecr.width
tabla = [(0, 600), (830, 1360), (1620, 1990), (2440, 2720), (W - 60, W)]
ancho = sum(b - a for a, b in tabla)
cab = [(0, 720), (W - 950, W)]
relleno = ancho - sum(b - a for a, b in cab)
cabecera = franjas(ecr, 0, 240, [(0, 720), (1000, 1000 + relleno), (W - 950, W)])
cuerpo = franjas(ecr, 240, ecr.height, tabla)
apilar([cabecera, cuerpo]).save(f'{D}/ecr-compacta.png')
for f in ['rds-compacta.png', 'ecr-compacta.png']:
    print(f, Image.open(f'{D}/{f}').size)
