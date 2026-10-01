-- Esquema inicial de BarberTurno según arquitectura §5.1 (T-06).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE usuario (
  id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre                  varchar(100) NOT NULL,
  correo                  varchar(254) NOT NULL,
  telefono                varchar(9),
  password_hash           varchar(100) NOT NULL,
  rol                     varchar(10)  NOT NULL CHECK (rol IN ('CLIENTE','BARBERO','ADMIN')),
  activo                  boolean      NOT NULL DEFAULT true,
  debe_cambiar_password   boolean      NOT NULL DEFAULT false,
  token_version           integer      NOT NULL DEFAULT 0,
  intentos_fallidos       smallint     NOT NULL DEFAULT 0,
  bloqueado_hasta         timestamptz,
  privacidad_aceptada_en  timestamptz,
  creado_en               timestamptz  NOT NULL,
  actualizado_en          timestamptz  NOT NULL,
  CONSTRAINT usuario_correo_minusculas CHECK (correo = lower(correo)),
  CONSTRAINT usuario_telefono_formato  CHECK (telefono IS NULL OR telefono ~ '^[0-9]{9}$'),
  CONSTRAINT usuario_cliente_completo  CHECK (rol <> 'CLIENTE' OR (telefono IS NOT NULL AND privacidad_aceptada_en IS NOT NULL))
);
CREATE UNIQUE INDEX usuario_correo_uk ON usuario (correo);

CREATE TABLE servicio (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre         varchar(80)  NOT NULL,
  descripcion    varchar(300) NOT NULL DEFAULT '',
  duracion_min   smallint     NOT NULL CHECK (duracion_min BETWEEN 10 AND 180 AND duracion_min % 10 = 0),
  precio         numeric(8,2) NOT NULL CHECK (precio >= 0),
  activo         boolean      NOT NULL DEFAULT true,
  creado_en      timestamptz  NOT NULL,
  actualizado_en timestamptz  NOT NULL
);
CREATE UNIQUE INDEX servicio_nombre_uk ON servicio (lower(nombre));

CREATE TABLE barbero (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id     bigint       NOT NULL UNIQUE REFERENCES usuario(id),
  especialidad   varchar(100) NOT NULL DEFAULT '',
  activo         boolean      NOT NULL DEFAULT true,
  creado_en      timestamptz  NOT NULL,
  actualizado_en timestamptz  NOT NULL
);

CREATE TABLE jornada (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  barbero_id   bigint   NOT NULL REFERENCES barbero(id),
  dia_semana   smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),   -- ISO: 1 = lunes
  hora_inicio  time     NOT NULL,
  hora_fin     time     NOT NULL,
  CONSTRAINT jornada_intervalo_valido CHECK (hora_inicio < hora_fin)
);
CREATE INDEX jornada_barbero_dia_ix ON jornada (barbero_id, dia_semana);

CREATE TABLE bloqueo (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  barbero_id  bigint       NOT NULL REFERENCES barbero(id),
  inicio      timestamptz  NOT NULL,
  fin         timestamptz  NOT NULL,
  motivo      varchar(200) NOT NULL,
  creado_por  bigint       NOT NULL REFERENCES usuario(id),
  creado_en   timestamptz  NOT NULL,
  CONSTRAINT bloqueo_intervalo_valido CHECK (inicio < fin)
);
CREATE INDEX bloqueo_barbero_inicio_ix ON bloqueo (barbero_id, inicio);

CREATE TABLE reserva (
  id                bigint GENERATED ALWAYS AS IDENTITY (START WITH 100) PRIMARY KEY,  -- se muestra como BT-<id>
  cliente_id        bigint       NOT NULL REFERENCES usuario(id),
  barbero_id        bigint       NOT NULL REFERENCES barbero(id),
  servicio_id       bigint       NOT NULL REFERENCES servicio(id),
  inicio            timestamptz  NOT NULL,
  fin               timestamptz  NOT NULL,
  duracion_ref_min  smallint     NOT NULL CHECK (duracion_ref_min > 0),
  precio_ref        numeric(8,2) NOT NULL CHECK (precio_ref >= 0),
  estado            varchar(12)  NOT NULL CHECK (estado IN
                      ('PENDIENTE','CONFIRMADA','EN_ATENCION','COMPLETADA','CANCELADA','NO_ASISTIO')),
  creada_por        bigint       NOT NULL REFERENCES usuario(id),
  version           integer      NOT NULL DEFAULT 0,
  creado_en         timestamptz  NOT NULL,
  actualizado_en    timestamptz  NOT NULL,
  CONSTRAINT reserva_fin_coherente CHECK (fin - inicio = make_interval(mins => duracion_ref_min)),
  CONSTRAINT reserva_sin_solape_barbero EXCLUDE USING gist
    (barbero_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado <> 'CANCELADA'),
  CONSTRAINT reserva_sin_solape_cliente EXCLUDE USING gist
    (cliente_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado <> 'CANCELADA')
);
CREATE INDEX reserva_barbero_inicio_ix ON reserva (barbero_id, inicio);
CREATE INDEX reserva_cliente_inicio_ix ON reserva (cliente_id, inicio);
CREATE INDEX reserva_inicio_ix         ON reserva (inicio);

CREATE TABLE auditoria_reserva (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reserva_id        bigint       NOT NULL REFERENCES reserva(id),
  actor_id          bigint       NOT NULL REFERENCES usuario(id),
  accion            varchar(12)  NOT NULL CHECK (accion IN
                      ('CREAR','REPROGRAMAR','CANCELAR','CONFIRMAR','INICIAR','COMPLETAR','NO_ASISTIO')),
  estado_anterior   varchar(12),
  estado_nuevo      varchar(12)  NOT NULL,
  datos_anteriores  jsonb,                 -- {inicio, fin, barberoId, estado}
  datos_nuevos      jsonb        NOT NULL,
  motivo            varchar(300),
  excepcional       boolean      NOT NULL DEFAULT false,   -- excepción administrativa (RN-08)
  creado_en         timestamptz  NOT NULL
);
CREATE INDEX auditoria_reserva_ix ON auditoria_reserva (reserva_id, creado_en);

CREATE TABLE notificacion (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id  bigint       NOT NULL REFERENCES usuario(id),
  reserva_id  bigint       NOT NULL REFERENCES reserva(id),
  tipo        varchar(12)  NOT NULL,      -- misma lista que auditoria_reserva.accion
  mensaje     varchar(300) NOT NULL,
  leida       boolean      NOT NULL DEFAULT false,
  creado_en   timestamptz  NOT NULL
);
CREATE INDEX notificacion_usuario_ix ON notificacion (usuario_id, leida, creado_en DESC);
