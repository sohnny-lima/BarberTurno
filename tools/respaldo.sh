#!/bin/sh
# Respaldo RNF-09. Uso: respaldo.sh BASE DIRECTORIO (fuera del repositorio).
# Pause las escrituras del origen durante dump y conteos; no use sh -x.
set -eu
umask 077
LC_ALL=C
export LC_ALL
directorio_script=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
. "$directorio_script/respaldo-comun.sh"
[ "$#" -eq 2 ] || fallar 'Uso: respaldo.sh BASE DIRECTORIO'
base=$1
validar_nombre "$base"
mkdir -p -- "$2"
destino=$(CDPATH= cd -- "$2" && pwd -P)
repositorio=$(CDPATH= cd -- "$directorio_script/.." && pwd -P)
case "$destino/" in "$repositorio/"*) fallar 'Guarde los respaldos fuera del repositorio.' ;; esac
bloqueo="$destino/.$base-respaldo.lock"
mkdir -- "$bloqueo" 2>/dev/null || fallar 'Ya hay un respaldo en curso o un bloqueo pendiente; revise el directorio.'
limpiar() { rm -f -- "$bloqueo/copia.dump" "$bloqueo/antes.tsv" "$bloqueo/despues.tsv"; rmdir -- "$bloqueo"; }
trap limpiar 0
trap 'exit 1' HUP INT TERM
archivo="$destino/$base-$(date -u +%Y%m%d-%H%M%S).dump"
[ ! -e "$archivo" ] && [ ! -e "$archivo.resumen.tsv" ] || fallar 'Ya existe un respaldo con esta fecha; no se sobrescribe.'
resumir "$base" "$bloqueo/antes.tsv" || fallar 'No se pudo contar el origen.'
if ! "$(binario pg_dump)" -w -Fc --file="$bloqueo/copia.dump" --dbname="$base"; then
    fallar 'pg_dump falló; no se publica un respaldo parcial ni se ejecuta retención.'
fi
[ -s "$bloqueo/copia.dump" ] || fallar 'pg_dump produjo un archivo vacío.'
resumir "$base" "$bloqueo/despues.tsv" || fallar 'No se pudo verificar el origen.'
cmp -s "$bloqueo/antes.tsv" "$bloqueo/despues.tsv" || fallar 'Cambió el conteo del origen; pause escrituras y repita.'
mv -- "$bloqueo/copia.dump" "$archivo"
mv -- "$bloqueo/antes.tsv" "$archivo.resumen.tsv"
printf 'Respaldo válido: %s\n' "$archivo"
# -mmin permite expresar estrictamente más de 14*24 horas. GNU find está
# disponible en Linux y Git Bash; no seguir enlaces ni descender subdirectorios.
find "$destino" -maxdepth 1 -type f \
    -name "$base-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9].dump" \
    -mmin +20160 -exec sh -eu -c '
        for copia do
            rm -f -- "$copia"
            resumen="$copia.resumen.tsv"
            if [ -f "$resumen" ] && [ ! -L "$resumen" ]; then rm -f -- "$resumen"; fi
        done
    ' sh {} +
