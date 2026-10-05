#!/bin/sh
# Pruebas de errores y retención sin conectarse a PostgreSQL.
set -eu
umask 077
directorio_script=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
temporal=$(mktemp -d "${TMPDIR:-/tmp}/barberturno-respaldo-test.XXXXXX")
# La ruta proviene exclusivamente de mktemp y está fuera del repositorio.
trap 'rm -rf -- "$temporal"' 0
trap 'exit 1' HUP INT TERM
mkdir "$temporal/bin" "$temporal/copias" "$temporal/copias/subdirectorio"
PG_BIN="$temporal/bin"
export PG_BIN
cat > "$PG_BIN/pg_dump" <<'SH'
#!/bin/sh
[ "${PRUEBA_DUMP:-}" != fallo ] || { echo 'pg_dump: fallo simulado' >&2; exit 7; }
for argumento do
    case "$argumento" in --file=*) archivo=${argumento#--file=} ;; esac
done
if [ "${PRUEBA_DUMP:-}" = vacio ]; then : > "$archivo"; else echo 'dump ficticio' > "$archivo"; fi
SH
cat > "$PG_BIN/psql" <<'SH'
#!/bin/sh
sql=$(cat)
case "$sql" in
    *'CREATE DATABASE'*)
        [ "${PRUEBA_EXISTE:-}" != si ] || { echo 'ERROR: la base de datos ya existe (simulado)' >&2; exit 3; } ;;
    *)
        filas=${PRUEBA_FILAS:-2}
        if [ -n "${PRUEBA_CAMBIA:-}" ]; then
            if [ -f "$PRUEBA_CAMBIA" ]; then filas=3; else : > "$PRUEBA_CAMBIA"; fi
        fi
        printf '7075626c6963\t%s\t%s\n' "${PRUEBA_TABLA:-7461626c61}" "$filas" ;;
esac
SH
cat > "$PG_BIN/pg_restore" <<'SH'
#!/bin/sh
if [ "${PRUEBA_RESTORE:-}" = invalido ]; then echo 'pg_restore: formato inválido simulado' >&2; exit 9; fi
if [ "$1" != --list ] && [ "${PRUEBA_RESTORE:-}" = fallo ]; then echo 'pg_restore: fallo transaccional simulado' >&2; exit 9; fi
SH
chmod +x "$PG_BIN/pg_dump" "$PG_BIN/psql" "$PG_BIN/pg_restore"
pruebas=0
aprobar() { pruebas=$((pruebas+1)); printf 'OK %s: %s\n' "$pruebas" "$1"; }
rechaza() {
    titulo=$1; shift
    if "$@" > "$temporal/salida" 2>&1; then echo "FALLO: $titulo" >&2; exit 1; fi
    aprobar "$titulo"
}
existe() { [ -f "$1" ] || { echo "FALLO: falta $1" >&2; exit 1; }; aprobar "$2"; }
ausente() { [ ! -e "$1" ] || { echo "FALLO: permanece $1" >&2; exit 1; }; aprobar "$2"; }
for script in respaldo-comun respaldo restaurar verificar-respaldo; do sh -n "$directorio_script/$script.sh"; done
aprobar 'sintaxis de cuatro scripts'
rechaza 'faltan argumentos' sh "$directorio_script/respaldo.sh"
rechaza 'nombre con ruta' sh "$directorio_script/respaldo.sh" '../demo' "$temporal/copias"
rechaza 'conexión en nombre rechazada' sh "$directorio_script/respaldo.sh" 'host=otro' "$temporal/copias"
rechaza 'destino dentro del repositorio' sh "$directorio_script/respaldo.sh" demo "$directorio_script"
mkdir "$temporal/copias/.demo-respaldo.lock"
rechaza 'respaldo concurrente' sh "$directorio_script/respaldo.sh" demo "$temporal/copias"
rmdir "$temporal/copias/.demo-respaldo.lock"
viejo="$temporal/copias/demo-20000101-000000.dump"
printf 'ficticio\n' > "$viejo"
printf 'resumen ficticio\n' > "$viejo.resumen.tsv"
touch -d '15 days ago' "$viejo"
PRUEBA_DUMP=fallo; export PRUEBA_DUMP
rechaza 'pg_dump no cero' sh "$directorio_script/respaldo.sh" demo "$temporal/copias"
existe "$viejo" 'fallo no aplica retención'
PRUEBA_DUMP=vacio
rechaza 'pg_dump vacío' sh "$directorio_script/respaldo.sh" demo "$temporal/copias"
ausente "$temporal/copias/.demo-respaldo.lock" 'fallos liberan el bloqueo'
unset PRUEBA_DUMP
PRUEBA_CAMBIA="$temporal/cambio"; export PRUEBA_CAMBIA
rechaza 'cambio de filas durante respaldo' sh "$directorio_script/respaldo.sh" demo "$temporal/copias"
existe "$viejo" 'cambio del origen no ejecuta retención'
ausente "$temporal/copias/.demo-respaldo.lock" 'cambio del origen libera bloqueo'
unset PRUEBA_CAMBIA
for nombre in demo-20000102-000000.dump otro-20000101-000000.dump demo-notas.dump demo-20000101-000000.dump.extra; do
    echo ficticio > "$temporal/copias/$nombre"
done
touch -d '13 days ago' "$temporal/copias/demo-20000102-000000.dump"
touch -d '30 days ago' "$temporal/copias/otro-20000101-000000.dump" "$temporal/copias/demo-notas.dump" "$temporal/copias/demo-20000101-000000.dump.extra"
echo ficticio > "$temporal/copias/subdirectorio/demo-20000101-000000.dump"
touch -d '30 days ago' "$temporal/copias/subdirectorio/demo-20000101-000000.dump"
mkdir "$temporal/copias/demo-19990101-000000.dump"
sh "$directorio_script/respaldo.sh" demo "$temporal/copias" > "$temporal/salida"
aprobar 'respaldo no vacío y resumen'
ausente "$viejo" 'retención elimina dump de 15 días'
ausente "$viejo.resumen.tsv" 'retención elimina solo resumen asociado'
existe "$temporal/copias/demo-20000102-000000.dump" 'retención conserva 13 días'
existe "$temporal/copias/otro-20000101-000000.dump" 'retención conserva otra base'
existe "$temporal/copias/demo-notas.dump" 'retención conserva otro patrón'
existe "$temporal/copias/demo-20000101-000000.dump.extra" 'retención conserva otra extensión'
existe "$temporal/copias/subdirectorio/demo-20000101-000000.dump" 'retención no desciende'
[ -d "$temporal/copias/demo-19990101-000000.dump" ]; aprobar 'retención no borra directorios con el patrón'
dump=$(sed -n 's/^Respaldo válido: //p' "$temporal/salida")
existe "$dump.resumen.tsv" 'resumen publicado junto al dump'
# Obligar una colisión independiente del segundo de ejecución.
mkdir "$temporal/path"
cat > "$temporal/path/date" <<'SH'
#!/bin/sh
echo 20000102-000000
SH
chmod +x "$temporal/path/date"
PATH="$temporal/path:$PATH" rechaza 'no sobrescribe un dump existente' sh "$directorio_script/respaldo.sh" demo "$temporal/copias"
rechaza 'dump inexistente' sh "$directorio_script/restaurar.sh" "$temporal/no.dump" nueva rol
cp "$dump" "$temporal/sin-resumen.dump"
rechaza 'sin origen ni resumen' sh "$directorio_script/restaurar.sh" "$temporal/sin-resumen.dump" nueva rol
PRUEBA_EXISTE=si; export PRUEBA_EXISTE
rechaza 'CREATE DATABASE rechaza destino existente' sh "$directorio_script/restaurar.sh" "$dump" nueva rol
unset PRUEBA_EXISTE
sh "$directorio_script/restaurar.sh" "$dump" nueva rol > "$temporal/salida"
aprobar 'restauración compara resumen exacto'
sh "$directorio_script/restaurar.sh" "$dump" nueva rol origen > "$temporal/salida"
aprobar 'restauración compara origen exacto'
PRUEBA_FILAS=3; export PRUEBA_FILAS
rechaza 'conteo de filas diferente' sh "$directorio_script/restaurar.sh" "$dump" nueva rol
unset PRUEBA_FILAS
PRUEBA_TABLA=6f747261; export PRUEBA_TABLA
rechaza 'tabla diferente con igual total de filas' sh "$directorio_script/restaurar.sh" "$dump" nueva rol
unset PRUEBA_TABLA
PRUEBA_RESTORE=fallo; export PRUEBA_RESTORE
rechaza 'pg_restore falla en la transacción' sh "$directorio_script/restaurar.sh" "$dump" nueva rol
PRUEBA_RESTORE=invalido
rechaza 'pg_restore inválido' sh "$directorio_script/restaurar.sh" "$dump" nueva rol
printf 'Resultado: %s pruebas aprobadas; PostgreSQL no utilizado.\n' "$pruebas"
