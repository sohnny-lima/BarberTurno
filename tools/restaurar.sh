#!/bin/sh
# Uso: restaurar.sh DUMP BASE_NUEVA ROL [BASE_ORIGEN]
# PGUSER crea en PGMAINTENANCE_DB (postgres por defecto). PGRESTORE_USER,
# PGRESTORE_PASSWORD o PGRESTORE_PASSFILE permiten restaurar con el rol dueño.
set -eu
umask 077
LC_ALL=C
export LC_ALL
directorio_script=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
. "$directorio_script/respaldo-comun.sh"
[ "$#" -ge 3 ] && [ "$#" -le 4 ] || fallar 'Uso: restaurar.sh DUMP BASE_NUEVA ROL [BASE_ORIGEN]'
dump=$1
base=$2
rol=$3
origen=${4:-}
validar_nombre "$base"
validar_nombre "$rol"
[ -z "$origen" ] || validar_nombre "$origen"
[ -f "$dump" ] && [ -s "$dump" ] || fallar 'El dump no existe o está vacío.'
[ -n "$origen" ] || [ -s "$dump.resumen.tsv" ] || fallar 'Indique el origen o proporcione el resumen del dump.'
"$(binario pg_restore)" --list "$dump" > /dev/null || fallar 'El archivo no es un dump válido.'
temporal=$(mktemp -d "${TMPDIR:-/tmp}/barberturno-restaurar.XXXXXX")
trap 'rm -f -- "$temporal/esperado.tsv" "$temporal/actual.tsv"; rmdir -- "$temporal"' 0
trap 'exit 1' HUP INT TERM
# CREATE DATABASE falla atómicamente si existe; no hay DROP ni --clean.
"$(binario psql)" -X -w -q -v ON_ERROR_STOP=1 \
    --dbname="${PGMAINTENANCE_DB:-postgres}" -v base="$base" -v rol="$rol" <<'SQL'
CREATE DATABASE :"base" OWNER :"rol" TEMPLATE template0;
SQL
# El administrador solo necesita acceso a la base de mantenimiento. En el
# ensayo el dueño se autentica con contraseña exclusivamente en el entorno.
if [ -n "${PGRESTORE_USER:-}" ]; then PGUSER=$PGRESTORE_USER; export PGUSER; fi
if [ "${PGRESTORE_PASSWORD+x}" = x ]; then PGPASSWORD=$PGRESTORE_PASSWORD; export PGPASSWORD; fi
if [ -n "${PGRESTORE_PASSFILE:-}" ]; then PGPASSFILE=$PGRESTORE_PASSFILE; export PGPASSFILE; fi
if ! "$(binario pg_restore)" -w --exit-on-error --single-transaction \
    --no-owner --role="$rol" --dbname="$base" "$dump"; then
    fallar "Restauración fallida; conserve y revise la base nueva $base. No se elimina automáticamente."
fi
if [ -n "$origen" ]; then
    resumir "$origen" "$temporal/esperado.tsv" || fallar 'No se pudo contar el origen.'
else
    cp -- "$dump.resumen.tsv" "$temporal/esperado.tsv"
fi
resumir "$base" "$temporal/actual.tsv" || fallar 'No se pudo contar el destino.'
cmp -s "$temporal/esperado.tsv" "$temporal/actual.tsv" || fallar 'No coinciden las tablas o las filas por tabla; revise la base nueva.'
tablas=$(awk 'END {print NR-1}' "$temporal/actual.tsv")
filas=$(awk -F '\t' 'NR>1 {total+=$3} END {print total+0}' "$temporal/actual.tsv")
printf 'Restauración verificada: %s; tablas=%s; filas=%s\n' "$base" "$tablas" "$filas"
