#!/bin/sh
# Utilidades internas de T-36; no recibe ni registra contraseñas.

fallar() {
    printf '%s\n' "Error: $*" >&2
    exit 1
}

validar_nombre() {
    case "$1" in
        ''|[!a-zA-Z_]*|*[!a-zA-Z0-9_]*) fallar 'Use nombres simples: letra o _, seguidos de letras, números o _.' ;;
    esac
    [ "${#1}" -le 63 ] || fallar 'El nombre excede 63 caracteres.'
}

binario() {
    if [ -n "${PG_BIN:-}" ]; then
        printf '%s/%s\n' "${PG_BIN%/}" "$1"
    else
        printf '%s\n' "$1"
    fi
}

# -X ignora psqlrc; -w evita solicitudes de contraseña en trabajos programados.
psql_base() {
    "$(binario psql)" -X -w -q -A -t -v ON_ERROR_STOP=1 --dbname="$1"
}

# Cada resumen lee todas las tablas en una única instantánea. Los nombres en
# hexadecimal permiten comparar incluso identificadores con tabulaciones.
resumir() {
    printf '%s\n' 'barberturno-resumen-v1' > "$2" || return 1
    psql_base "$1" >> "$2" <<'SQL'
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT format(
    'SELECT %L || chr(9) || %L || chr(9) || count(*)::text FROM %I.%I;',
    encode(convert_to(n.nspname, 'UTF8'), 'hex'),
    encode(convert_to(c.relname, 'UTF8'), 'hex'), n.nspname, c.relname)
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind IN ('r', 'p')
  AND n.nspname <> 'information_schema' AND n.nspname !~ '^pg_'
ORDER BY n.nspname COLLATE "C", c.relname COLLATE "C"
\gexec
COMMIT;
SQL
}
