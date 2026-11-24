#!/bin/sh
# El vídeo de la demo en AWS (docs/demo-aws.mp4), a partir de las capturas de docs/capturas.
# Hace falta Python con Pillow y ffmpeg (las fuentes van en herramientas/video/fuentes). Desde plataforma/:
#   sh herramientas/video/montar.sh
set -e
T=$(mktemp -d)
cp docs/capturas/aws-*.png "$T"/
python3 herramientas/video/recomponer.py "$T"
python3 herramientas/video/diapos.py "$T"
cd "$T"
# Grafana: 3 s quieta arriba para leerla y luego baja despacio hasta el final
ffmpeg -y -loglevel error -loop 1 -t 9 -i 06-marco.png -loop 1 -t 9 -i aws-grafana.png -filter_complex "[1]scale=1100:-1,crop=1100:700:0:'max(0\,min((t-3)/5.5\,1))*(ih-700)'[g];[0][g]overlay=x=(W-1100)/2:y=310,format=yuv420p" -r 30 -t 9 06.mp4
for i in 00 01 02 03 04 05 07; do
	d=6; [ $i = 00 ] && d=5; [ $i = 07 ] && d=7
	ffmpeg -y -loglevel error -loop 1 -t $d -i $i.png -vf "scale=1920:1080,format=yuv420p" -r 30 $i.mp4
done
ffmpeg -y -loglevel error -i 00.mp4 -i 01.mp4 -i 02.mp4 -i 03.mp4 -i 04.mp4 -i 05.mp4 -i 06.mp4 -i 07.mp4 -filter_complex "[0][1]xfade=fade:duration=0.6:offset=4.4[a];[a][2]xfade=fade:duration=0.6:offset=9.8[b];[b][3]xfade=fade:duration=0.6:offset=15.2[c];[c][4]xfade=fade:duration=0.6:offset=20.6[d];[d][5]xfade=fade:duration=0.6:offset=26[e];[e][6]xfade=fade:duration=0.6:offset=31.4[f];[f][7]xfade=fade:duration=0.6:offset=39.8,format=yuv420p[v]" -map "[v]" -c:v libx264 -crf 22 -preset slow -movflags +faststart demo-aws.mp4
cd - >/dev/null
mv "$T/demo-aws.mp4" docs/demo-aws.mp4
rm -rf "$T"
echo "docs/demo-aws.mp4"
