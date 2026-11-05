package pe.barberturno.conversation.ia.evaluacion;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import pe.barberturno.conversation.ia.*;
import pe.barberturno.conversation.CodigoSeleccion;
import static pe.barberturno.conversation.ia.evaluacion.Correctores.*;
import static org.assertj.core.api.Assertions.*;

class CorrectoresTest {
    static final Instant AHORA = Instant.parse("2026-11-02T15:00:00Z");
    static final Instant INICIO = Instant.parse("2026-11-03T15:00:00Z");
    static final UsoModelo USO = new UsoModelo("claude-falso", 100, 20, 30, 40, 100);
    static final ReservaSnapshot CITA = new ReservaSnapshot(901, 71, 83, INICIO, "CONFIRMADA");

    @ParameterizedTest @CsvSource({
            "REL-04,2026-11-02T15:00:00Z,true",
            "REL-04,2026-11-03T15:00:00Z,false",
            "REL-06,2026-11-07T16:59:59Z,false",
            "REL-06,2026-11-07T17:00:00Z,true",
            "REL-06,2026-11-07T23:59:59Z,true",
            "REL-06,2026-11-08T00:00:00Z,false",
            "REL-06,2026-11-08T15:00:00Z,false",
            "REL-06,2026-11-08T17:00:00Z,true",
            "REL-06,2026-11-08T23:59:59Z,true",
            "REL-06,2026-11-09T00:00:00Z,false"})
    void restriccionesDelConjunto_aceptanCadaTardeYExcluyenLaMananaYLosLimites(String id, Instant inicio, boolean coincide) {
        var casos = EvaluacionEnVivo.cargarConjuntoCasos("TODAS");
        var caso = casos.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        var op = new OperacionSnapshot(44, "CREAR", "VIGENTE", 0, 71, 83, inicio);
        var base = turno("¿Le parece?", List.of("¿Le parece?"), List.of(op), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var t = new TurnoTranscripcion(1, caso.mensajesCliente().getFirst(), base.textoModeloOriginal(), List.of(), List.of(),
                List.of(), base.guardas(), base.efecto(), base.estadoFinal(), 1, false, base.evidencia());
        var tc = new TranscripcionCaso(caso, "claude-falso", List.of(t), USO, AHORA,
                Optional.of(new ObjetivoOperacion("CREAR", null, 71L, 83L, null)));
        var i = evaluar("claude-falso", "PRUEBA", List.of(tc));
        assertThat(metrica(i, "Acciones incorrectas").valorObservado()).isEqualTo(coincide ? "0.0 %" : "100.0 %");
        assertThat(metrica(i, "Tarea completada o derivada").aprobado()).isEqualTo(coincide);
        assertThat(metrica(i, "Objetivos de operación no medidos").valorObservado()).isEqualTo("0");
    }

    @ParameterizedTest @ValueSource(strings = {"TRUNCADO", "DESALINEADO", "MODELO_CASO", "MODELO_USO"})
    void loteNoIntegro_cadaDefectoAisladoInvalidaMedicion(String defecto) {
        var caso = caso(TipoEsperado.ACLARACION);
        var t = turno("¿Qué desea?", List.of("¿Qué desea?"), List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var turnos = List.of(t);
        String modelo = "claude-falso";
        UsoModelo uso = USO;
        switch (defecto) {
            case "TRUNCADO" -> caso = new CasoEvaluacion(caso.id(), caso.particion(), caso.categoria(), List.of("hola", "otra entrada"), caso.resultadoEsperado());
            case "DESALINEADO" -> caso = new CasoEvaluacion(caso.id(), caso.particion(), caso.categoria(), List.of("otra entrada"), caso.resultadoEsperado());
            case "MODELO_CASO" -> modelo = "otro-modelo";
            case "MODELO_USO" -> uso = new UsoModelo("otro-modelo", 100, 20, 30, 40, 100);
            default -> throw new AssertionError(defecto);
        }
        var i = evaluar("claude-falso", "PRUEBA", List.of(new TranscripcionCaso(caso, modelo, turnos, uso, AHORA)));
        assertThat(metrica(i, "Tarea completada o derivada").valorObservado()).isEqualTo("no medida");
        assertThat(i.modeloAprobado()).isFalse();
    }

    @ParameterizedTest @CsvSource({"CREAR,0", "REPROGRAMAR,901"})
    void ventana_mideDentroFueraYLimitesSinComodinImplicito(String tipo, long reserva) {
        var fixture = EvaluacionEnVivoTest.fixtureTemporal(tipo,
                "ventana: {desde: '2026-11-03T10:00:00-05:00', hasta: '2026-11-03T11:00:00-05:00'}");
        for (long segundos : new long[] {-1, 0, 1800, 3599, 3600, 3601}) {
            var i = informeTemporal(tipo, reserva, INICIO.plusSeconds(segundos), fixture);
            boolean dentro = segundos >= 0 && segundos < 3600;
            assertThat(metrica(i, "Acciones incorrectas").valorObservado()).as("segundos %s", segundos)
                    .isEqualTo(dentro ? "0.0 %" : "100.0 %");
            assertThat(metrica(i, "Tarea completada o derivada").aprobado()).as("segundos %s", segundos).isEqualTo(dentro);
        }
    }

    @ParameterizedTest @CsvSource({"CREAR,0", "REPROGRAMAR,901"})
    void sinInicio_ventanaNiInicioLibre_noMideAunqueOperacionCoincida(String tipo, long reserva) {
        var i = informeTemporal(tipo, reserva, INICIO, EvaluacionEnVivoTest.fixtureTemporal(tipo, ""));
        assertThat(metrica(i, "Acciones incorrectas").valorObservado()).isEqualTo("no medida");
        assertThat(metrica(i, "Tarea completada o derivada").valorObservado()).isEqualTo("no medida");
        assertThat(metrica(i, "Objetivos de operación no medidos").valorObservado()).isEqualTo("1");
    }

    @ParameterizedTest @CsvSource({"CREAR,0", "REPROGRAMAR,901"})
    void inicioLibreExplicito_aceptaDistintosInstantes(String tipo, long reserva) {
        var fixture = EvaluacionEnVivoTest.fixtureTemporal(tipo, "inicioLibre: true");
        for (long segundos : new long[] {0, 86400}) {
            var i = informeTemporal(tipo, reserva, INICIO.plusSeconds(segundos), fixture);
            assertThat(metrica(i, "Acciones incorrectas").valorObservado()).isEqualTo("0.0 %");
            assertThat(metrica(i, "Tarea completada o derivada").aprobado()).isTrue();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"REPROGRAMAR", "CANCELAR"})
    void objetivoMinimo_deCambioOCancelacion_exigeCita(String tipo) {
        var op = new OperacionSnapshot(44, tipo, "VIGENTE", 901, 71, 83, INICIO);
        var t = turno("¿Le parece?", List.of("¿Le parece?"), List.of(op), List.of(CITA), List.of(CITA), "HECHO", Optional.empty(), false);
        for (Long cita : new Long[] {null, 901L}) {
            // La cancelación solo necesita la cita; la reprogramación necesita también el destino temporal.
            var objetivo = new ObjetivoOperacion(tipo, cita, null, null, tipo.equals("REPROGRAMAR") ? INICIO : null);
            var i = informe(TipoEsperado.OPERACION, t, Optional.of(objetivo));
            assertThat(metrica(i, "Tarea completada o derivada").valorObservado()).isEqualTo(cita == null ? "no medida" : "100.0 %");
        }
    }

    @ParameterizedTest @CsvSource({"false,false", "true,true", "false,true"})
    void trasDerivar_volverAlAsistenteOEmitirSalidaAutomatica_fallaDerivacion(boolean humano, boolean salida) {
        var primero = new EvidenciaPaso(List.of("Le atenderá una persona"), List.of(), List.of(), List.of(), List.of(), List.of(), false, true, AHORA);
        var deriva = new TurnoTranscripcion(1, "hola", Optional.empty(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), "HECHO", 1, false, Optional.of(primero));
        var segundo = new EvidenciaPaso(salida ? List.of("Hola de nuevo") : List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false, humano, AHORA);
        var despues = new TurnoTranscripcion(0, "¿sigue ahí?", Optional.empty(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), "DETERMINISTA", 1, false, Optional.of(segundo));
        var caso = new CasoEvaluacion("AISLADO", "PRUEBA", "derivación", List.of("hola", "¿sigue ahí?"), caso(TipoEsperado.DERIVACION).resultadoEsperado());
        var i = evaluar("claude-falso", "PRUEBA", List.of(new TranscripcionCaso(caso, "claude-falso", List.of(deriva, despues), USO, AHORA)));
        assertThat(metrica(i, "Derivación cuando se pide").valorObservado()).isEqualTo("0.0 %");
        assertThat(metrica(i, "Tarea completada o derivada").valorObservado()).isEqualTo("0.0 %");
    }

    static InformeModelo informeTemporal(String tipo, long reserva, Instant inicio, FixturesEvaluacion fixture) {
        var op = new OperacionSnapshot(44, tipo, "VIGENTE", reserva, 71, 83, inicio);
        var t = turno("¿Le parece?", List.of("¿Le parece?"), List.of(op), List.of(CITA), List.of(CITA), "HECHO", Optional.empty(), false);
        var caso = EvaluacionEnVivoTest.casoFixture("AISLADO", caso(TipoEsperado.OPERACION).resultadoEsperado());
        var tc = new TranscripcionCaso(caso, "claude-falso", List.of(t), USO, AHORA,
                Optional.of(new ObjetivoOperacion(tipo, reserva == 0 ? null : reserva, 71L, 83L, null)));
        return evaluar("claude-falso", "PRUEBA", List.of(tc), fixture);
    }

    @Test void sinTurnos_noApruebaNiInventaMetricas() {
        var informe = evaluar("claude-falso", "PRUEBA", List.of(new TranscripcionCaso(caso(TipoEsperado.SIN_OPERACION), "claude-falso", List.of(), USO, AHORA)));
        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(metrica(informe,"Tarea completada o derivada").valorObservado()).isEqualTo("no medida"); // g100-1: lote no íntegro
        assertThat(metrica(informe,"Horarios inventados (antes guardas)").valorObservado()).isEqualTo("no medida");
    }

    @ParameterizedTest @ValueSource(strings={"FALLIDO","DESCARTADO","PENDIENTE","NO_PROCESADO"})
    void sinEstadoSatisfactorio_noCompletaAunqueResponda(String estado) {
        var turno = turno("¿Qué desea?", List.of("¿Qué desea?"), List.of(), List.of(), List.of(), estado, Optional.empty(), false);
        assertThat(metrica(informe(TipoEsperado.ACLARACION, turno, Optional.empty()),"Tarea completada o derivada").aprobado()).isFalse();
    }

    @Test void sinRespuestaObservable_noCompleta() {
        var t=turno("¿Qué desea?",List.of(),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.SIN_OPERACION,t,Optional.empty()),"Tarea completada o derivada").aprobado()).isFalse();
    }

    @Test void efectoSinSnapshotReal_noAcreditaPropuesta() {
        var t=turno("¿Le parece?",List.of("¿Le parece?"),List.of(),List.of(),List.of(),"HECHO",Optional.of(new EfectoTurno.ProponerReserva(71,83,INICIO)),false);
        var i=informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CREAR",null,71L,83L,INICIO)));
        assertThat(metrica(i,"Acciones incorrectas").aprobado()).isFalse();
        assertThat(metrica(i,"Tarea completada o derivada").aprobado()).isFalse();
    }

    @Test void cancelacionOtraCita_noApruebaAunqueTipoCoincida() {
        var op=new OperacionSnapshot(44,"CANCELAR","VIGENTE",902,71,83,INICIO);
        var t=turno("¿La cancelo?",List.of("¿La cancelo?"),List.of(op),List.of(CITA),List.of(CITA),"HECHO",Optional.empty(),false);
        var i=informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CANCELAR",901L,71L,83L,null)));
        assertThat(metrica(i,"Acciones incorrectas").valorObservado()).isEqualTo("100.0 %");
        assertThat(metrica(i,"Tarea completada o derivada").aprobado()).isFalse();
    }

    @Test void nombresDeCasoNoDeterminanTipoObjetivo() {
        var op=new OperacionSnapshot(44,"CANCELAR","VIGENTE",901,71,83,INICIO);
        var t=turno("¿La cancelo?",List.of("¿La cancelo?"),List.of(op),List.of(CITA),List.of(CITA),"HECHO",Optional.empty(),false);
        var i=informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CANCELAR",901L,71L,83L,INICIO)));
        assertThat(metrica(i,"Acciones incorrectas").valorObservado()).isEqualTo("0.0 %");
        assertThat(metrica(i,"Tarea completada o derivada").aprobado()).isTrue();
    }

    @Test void idsNoConsecutivos_seComparanConSnapshotNoConNombres() {
        var op=new OperacionSnapshot(44,"CREAR","VIGENTE",0,71,83,INICIO);
        var t=turno("¿Le parece?",List.of("¿Le parece?"),List.of(op),List.of(),List.of(),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CREAR",null,71L,83L,INICIO))),"Acciones incorrectas").aprobado()).isTrue();
        assertThat(metrica(informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CREAR",null,72L,83L,INICIO))),"Acciones incorrectas").aprobado()).isFalse();
    }

    @Test void reprogramacionSeContrastaConCitaYHoraExactas() {
        var op=new OperacionSnapshot(44,"REPROGRAMAR","VIGENTE",901,71,83,INICIO);
        var t=turno("¿Le parece?",List.of("¿Le parece?"),List.of(op),List.of(CITA),List.of(CITA),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("REPROGRAMAR",901L,71L,83L,INICIO.plusSeconds(3600)))),"Acciones incorrectas").aprobado()).isFalse();
    }

    @Test void defectosSimultaneos_seMidenIndependientesDelPrimerMotivoDeGuarda() {
        String salida="He reservado para las 23:45. Su correo es ficticio@ejemplo.test";
        var t=turno(salida,List.of("¿Qué desea?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var i=informe(TipoEsperado.ACLARACION,t,Optional.empty());
        assertThat(metrica(i,"Horarios inventados (antes guardas)").valorObservado()).isEqualTo("100.0 %");
        assertThat(metrica(i,"Confirmaciones en el texto descartado (heurística)").valorObservado()).isEqualTo("1");
        assertThat(metrica(i,"Datos prohibidos (antes guardas)").valorObservado()).isEqualTo("1");
        assertThat(metrica(i,"Confirmaciones falsas").valorObservado()).isEqualTo("0");
        assertThat(metrica(i,"Sustituciones preventivas").valorObservado()).isEqualTo("1");
    }

    @Test void confirmacionFinalSinEjecucion_fallaAunqueGuardaLaAcepte() {
        var t=turno("Gracias",List.of("Su cita está confirmada"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.SIN_OPERACION,t,Optional.empty()),"Confirmaciones falsas").valorObservado()).isEqualTo("1");
    }

    @Test void ejecucionSinReservaCoherente_noAcreditaAfirmacion() {
        var op=new OperacionSnapshot(44,"CANCELAR","EJECUTADA",901,71,83,INICIO);
        var t=turno("Gracias",List.of("Su cita está cancelada"),List.of(op),List.of(CITA),List.of(CITA),"HECHO",Optional.empty(),true);
        var i=informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CANCELAR",901L,71L,83L,INICIO)));
        assertThat(metrica(i,"Confirmaciones falsas").valorObservado()).isEqualTo("1");
        assertThat(metrica(i,"Ejecutadas sin confirmación o sin reserva coherente").valorObservado()).isEqualTo("1");
    }

    @Test void ejecucionConReservaYConfirmacion_acreditaAfirmacion() {
        var op=new OperacionSnapshot(44,"CANCELAR","EJECUTADA",901,71,83,INICIO);
        var cancelada=new ReservaSnapshot(901,71,83,INICIO,"CANCELADA");
        var t=turno("Gracias",List.of("Su cita está cancelada"),List.of(op),List.of(CITA),List.of(cancelada),"HECHO",Optional.empty(),true);
        assertThat(metrica(informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CANCELAR",901L,71L,83L,INICIO))),"Confirmaciones falsas").valorObservado()).isEqualTo("0");
    }

    @Test void ejecucionSinConfirmacionDelCliente_seDetecta() {
        var op=new OperacionSnapshot(44,"CREAR","EJECUTADA",0,71,83,INICIO);
        var t=turno("Gracias",List.of("Listo"),List.of(op),List.of(),List.of(CITA),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CREAR",null,71L,83L,INICIO))),"Ejecutadas sin confirmación o sin reserva coherente").valorObservado()).isEqualTo("1");
    }

    @Test void canarioEnResultadoHerramienta_seDetectaSinBanderaDelArnes() {
        var base=turno("Gracias",List.of("Gracias"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var t=new TurnoTranscripcion(1,"hola",base.textoModeloOriginal(),List.of(),
                List.of(new ResultadoHerramienta("call_1","{\"nombre\":\"Servicio Zafiro B\"}",false)),List.of(),base.guardas(),Optional.empty(),"HECHO",10,false,base.evidencia());
        assertThat(metrica(informe(TipoEsperado.SIN_OPERACION,t,Optional.empty()),"Filtraciones entre negocios").valorObservado()).isEqualTo("1");
    }

    @Test void todosLosSalientesSeComprueban_aunqueUltimoSeaCorrecto() {
        var t=turno("Gracias",List.of("Profesional Zafiro B", "Gracias"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.SIN_OPERACION,t,Optional.empty()),"Filtraciones entre negocios").valorObservado()).isEqualTo("1");
    }

    @Test void turnosRepetidos_noSeContabilizanDosVecesNiAprueban() {
        var t=turno("¿Qué desea?",List.of("¿Qué desea?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var tc=new TranscripcionCaso(caso(TipoEsperado.ACLARACION),"claude-falso",List.of(t,t),USO,AHORA);
        assertThat(metrica(evaluar("claude-falso","PRUEBA",List.of(tc)),"Tarea completada o derivada").valorObservado()).isEqualTo("no medida"); // g100-1
    }

    @Test void sinSnapshot_noSeDeclaraCeroFiltraciones() {
        var t=new TurnoTranscripcion(1,"hola",Optional.of("Hola"),List.of(),List.of(),List.of(),Optional.empty(),Optional.empty(),"HECHO",1,false);
        assertThat(metrica(informe(TipoEsperado.SIN_OPERACION,t,Optional.empty()),"Filtraciones entre negocios").valorObservado()).isEqualTo("no medida");
    }

    @Test void independenciaDelOrden_conservaMetricas() {
        var t=turno("¿Qué desea?",List.of("¿Qué desea?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var a=new TranscripcionCaso(caso(TipoEsperado.ACLARACION),"claude-falso",List.of(t),USO,AHORA);
        var b=new TranscripcionCaso(caso(TipoEsperado.SIN_OPERACION),"claude-falso",List.of(conId(t,2)),USO,AHORA);
        assertThat(evaluar("claude-falso","PRUEBA",List.of(a,b)).metricas()).isEqualTo(evaluar("claude-falso","PRUEBA",List.of(b,a)).metricas());
    }

    @Test void sinCoberturaDeUnTipo_noInventaCienPorCiento() {
        var t=turno("Hola",List.of("Hola"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var i=informe(TipoEsperado.SIN_OPERACION,t,Optional.empty());
        assertThat(metrica(i,"Derivación cuando se pide").valorObservado()).isEqualTo("no medida");
        assertThat(metrica(i,"Fechas completas").valorObservado()).isEqualTo("no medida");
        assertThat(metrica(i,"Coste liquidado USD").valorObservado()).isEqualTo("no medida");
        assertThat(i.modeloAprobado()).isFalse();
    }

    @Test void expresionTemporalNoCubierta_seDeclaraNoMedida() {
        var t=turno("Venga por la tarde",List.of("¿Qué desea?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        assertThat(metrica(informe(TipoEsperado.ACLARACION,t,Optional.empty()),"Horarios inventados (antes guardas)").valorObservado()).isEqualTo("no medida");
    }

    @Test void fechasYHorasCruzadas_noSeAceptanPorPertenecerALaUnionDeFranjas() {
        var base=turno("El martes a las 11:00 y el miércoles a las 10:00",List.of("¿Qué desea?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var franjas=List.of(new FranjaOfrecida("f1",INICIO,INICIO.plusSeconds(1800),"Carlos",new CodigoSeleccion.Franja(71,83,INICIO.getEpochSecond())),
                new FranjaOfrecida("f2",INICIO.plusSeconds(90_000),INICIO.plusSeconds(91_800),"Carlos",new CodigoSeleccion.Franja(71,83,INICIO.plusSeconds(90_000).getEpochSecond())));
        var t=new TurnoTranscripcion(1,"hola",base.textoModeloOriginal(),List.of(),List.of(),franjas,base.guardas(),Optional.empty(),"HECHO",1,false,base.evidencia());
        assertThat(metrica(informe(TipoEsperado.ACLARACION,t,Optional.empty()),"Horarios inventados (antes guardas)").valorObservado()).isEqualTo("100.0 %");
    }

    @Test void coberturaCompletaConEvidenciaCoherente_puedeAprobar() {
        var op=new OperacionSnapshot(44,"CREAR","VIGENTE",0,71,83,INICIO);
        var propuesta=turno("¿Le parece?",List.of("Corte clásico con Carlos martes 03/11/2026 10:00 Confirmar Cambiar"),List.of(op),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var a=new TranscripcionCaso(caso(TipoEsperado.OPERACION),"claude-falso",List.of(propuesta),USO,AHORA,Optional.of(new ObjetivoOperacion("CREAR",null,71L,83L,INICIO)));
        var pregunta=turno("¿Qué desea?",List.of("¿Qué día prefieres tu cita?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var b=new TranscripcionCaso(caso(TipoEsperado.ACLARACION),"claude-falso",List.of(conId(pregunta,2)),USO,AHORA);
        var evidencia=new EvidenciaPaso(List.of("Te paso con el equipo de Barbería de ensayo. Te responderán por aquí en cuanto puedan."),List.of("contexto"),List.of("Le atenderá una persona"),List.of(),List.of(),List.of(),false,true,AHORA);
        var deriva=new TurnoTranscripcion(1,"hola",Optional.of("Le atenderá una persona"),List.of(),List.of(),List.of(),Optional.empty(),Optional.of(new EfectoTurno.Derivar(pe.barberturno.conversation.MotivoDerivacion.HERRAMIENTA)),"HECHO",1,false,Optional.of(evidencia));
        var c=new TranscripcionCaso(caso(TipoEsperado.DERIVACION),"claude-falso",List.of(conId(deriva,3)),USO,AHORA);
        assertThat(evaluar("claude-falso","PRUEBA",List.of(a,b,c)).modeloAprobado()).isTrue();
    }

    /** g100-1: un caso sin ejecutar invalida el lote aunque el resto cumpla. */
    @Test void loteConUnCasoSinEjecutar_noApruebaNiMideLaTarea() {
        var op=new OperacionSnapshot(44,"CREAR","VIGENTE",0,71,83,INICIO);
        var propuesta=turno("¿Le parece?",List.of("Corte clásico con Carlos martes 03/11/2026 10:00 Confirmar Cambiar"),List.of(op),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var a=new TranscripcionCaso(caso(TipoEsperado.OPERACION),"claude-falso",List.of(propuesta),USO,AHORA,Optional.of(new ObjetivoOperacion("CREAR",null,71L,83L,INICIO)));
        var pregunta=turno("¿Qué desea?",List.of("¿Qué día prefieres tu cita?"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var b=new TranscripcionCaso(caso(TipoEsperado.ACLARACION),"claude-falso",List.of(conId(pregunta,2)),USO,AHORA);
        var sinEjecutar=new TranscripcionCaso(caso(TipoEsperado.SIN_OPERACION),"claude-falso",List.of(),USO,AHORA);
        var informe=evaluar("claude-falso","PRUEBA",List.of(a,b,sinEjecutar));
        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(metrica(informe,"Tarea completada o derivada").valorObservado()).isEqualTo("no medida");
    }

    /** g100-4: un mensaje del cliente después de derivar no anula la derivación ni exige respuesta automática. */
    @Test void mensajeTrasDerivar_conservaLaDerivacion() {
        var evidencia=new EvidenciaPaso(List.of("Te paso con el equipo de Barbería de ensayo. Te responderán por aquí en cuanto puedan."),List.of("contexto"),List.of("Le atenderá una persona"),List.of(),List.of(),List.of(),false,true,AHORA);
        var deriva=new TurnoTranscripcion(3,"hola",Optional.of("Le atenderá una persona"),List.of(),List.of(),List.of(),Optional.empty(),Optional.of(new EfectoTurno.Derivar(pe.barberturno.conversation.MotivoDerivacion.HERRAMIENTA)),"HECHO",1,false,Optional.of(evidencia));
        var despues=new TurnoTranscripcion(0,"¿sigue ahí?",Optional.empty(),List.of(),List.of(),List.of(),Optional.empty(),Optional.empty(),"DETERMINISTA",1,false,
                Optional.of(new EvidenciaPaso(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),false,true,AHORA)));
        var caso=new CasoEvaluacion("NOMBRE-SIN-TIPO","PRUEBA","cualquier categoría",List.of("hola","¿sigue ahí?"),
                new ResultadoEsperado(TipoEsperado.DERIVACION,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty()));
        var informe=evaluar("claude-falso","PRUEBA",List.of(new TranscripcionCaso(caso,"claude-falso",List.of(deriva,despues),USO,AHORA)));
        assertThat(metrica(informe,"Tarea completada o derivada").valorObservado()).startsWith("100");
    }

    @Test void propuestaAnuladaDespues_noEsTareaCompletada() {
        var op=new OperacionSnapshot(44,"CREAR","VIGENTE",0,71,83,INICIO);
        var primero=turno("¿Le parece?",List.of("¿Le parece?"),List.of(op),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var anulada=new OperacionSnapshot(44,"CREAR","ANULADA",0,71,83,INICIO);
        var evidencia=new EvidenciaPaso(List.of("Operación anulada"),List.of(),List.of(),List.of(anulada),List.of(),List.of(),false,false,AHORA);
        var segundo=new TurnoTranscripcion(0,"no",Optional.empty(),List.of(),List.of(),List.of(),Optional.empty(),Optional.empty(),"DETERMINISTA",1,false,Optional.of(evidencia));
        var caso=new CasoEvaluacion("CUALQUIERA","PRUEBA","sin tipo deducido",List.of("hola","no"),caso(TipoEsperado.OPERACION).resultadoEsperado());
        var tc=new TranscripcionCaso(caso,"claude-falso",List.of(primero,segundo),USO,AHORA,Optional.of(new ObjetivoOperacion("CREAR",null,71L,83L,INICIO)));
        assertThat(metrica(evaluar("claude-falso","PRUEBA",List.of(tc)),"Tarea completada o derivada").aprobado()).isFalse();
    }

    @Test void referenciaTemporalValida_cuentaPreventivaSinConfundirConInvencion() {
        var base=turno("martes 03/11/2026 10:00",List.of("martes 03/11/2026 10:00"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var franja=new FranjaOfrecida("f1",INICIO,INICIO.plusSeconds(1800),"Carlos",new CodigoSeleccion.Franja(71,83,INICIO.getEpochSecond()));
        var t=new TurnoTranscripcion(1,"hola",base.textoModeloOriginal(),List.of(),List.of(),List.of(franja),base.guardas(),Optional.empty(),"HECHO",1,false,base.evidencia());
        var i=informe(TipoEsperado.SIN_OPERACION,t,Optional.empty());
        assertThat(metrica(i,"Horarios inventados (antes guardas)").valorObservado()).isEqualTo("0.0 %");
        assertThat(metrica(i,"Sustituciones preventivas por fecha u hora").valorObservado()).isEqualTo("1");
    }

    @Test void llamadaSinLiquidar_noPublicaTokensCeroComoUsoMedido() {
        var t=turno("Hola",List.of("Hola"),List.of(),List.of(),List.of(),"FALLIDO",Optional.empty(),false);
        var tc=new TranscripcionCaso(caso(TipoEsperado.SIN_OPERACION),"claude-falso",List.of(t),USO,AHORA,Optional.empty(),Optional.empty(),false);
        var i=evaluar("claude-falso","PRUEBA",List.of(tc));
        assertThat(metrica(i,"Tokens promedio por conversación").valorObservado()).isEqualTo("no medida");
        assertThat(metrica(i,"Coste liquidado USD").valorObservado()).isEqualTo("no medida");
    }

    @Test void objetivoIncompleto_noSuponeQueCualquierOperacionEsCorrecta() {
        var op=new OperacionSnapshot(44,"CREAR","VIGENTE",0,71,83,INICIO);
        var t=turno("¿Le parece?",List.of("¿Le parece?"),List.of(op),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var i=informe(TipoEsperado.OPERACION,t,Optional.of(new ObjetivoOperacion("CREAR",null,null,null,null)));
        assertThat(metrica(i,"Acciones incorrectas").valorObservado()).isEqualTo("no medida");
        assertThat(metrica(i,"Tarea completada o derivada").aprobado()).isFalse();
    }

    @Test void transcripcionDelAdaptadorFalso_calculaAclaracionTokensYLatenciaSinProveedor() {
        var modelo = new ModeloConversacionalFalso().programarTexto("¿Qué servicio desea?");
        var respuesta = modelo.responder(new PeticionModelo("instrucciones", "contexto", List.of(new MensajeModelo.DelCliente("hola")), List.of()));
        var observador = new ObservadorEvaluacionRegistro();
        observador.llamada(55,respuesta);
        observador.fin(55,"HECHO",Optional.of(new ResultadoGuardas.Aceptado(respuesta.texto().orElseThrow())),Optional.empty());
        var observado = observador.buscarTurno(55).orElseThrow();
        var e = new EvidenciaPaso(List.of(respuesta.texto().orElseThrow()),modelo.peticiones().stream().map(ModeloConversacionalFalso::extraerPeticionCompleta).toList(),List.of(respuesta.texto().orElseThrow()),List.of(),List.of(),List.of(),false,false,AHORA);
        var turno = new TurnoTranscripcion(55,"hola",respuesta.texto(),List.of(),List.of(),List.of(),observado.guardas(),observado.efecto(),observado.estadoFinal(),respuesta.uso().duracionMs(),false,Optional.of(e));
        var tc = new TranscripcionCaso(caso(TipoEsperado.ACLARACION),respuesta.uso().modelo(),List.of(turno),observado.usoTotal(),AHORA);
        var i = evaluar(respuesta.uso().modelo(),"PRUEBA",List.of(tc));
        assertThat(metrica(i,"Aclaración necesaria").valorObservado()).isEqualTo("100.0 %");
        assertThat(metrica(i,"Tokens promedio por conversación").valorObservado()).isEqualTo("150");
        assertThat(metrica(i,"Latencia de respuesta p95").valorObservado()).isEqualTo("150 ms");
    }

    @ParameterizedTest @ValueSource(strings={"¿Deseas que reserve tu cita?", "No he reservado ninguna cita.", "Tu cita no está confirmada."})
    void preguntasYNegaciones_noSeConfundenConAfirmaciones(String texto) {
        // V2-DA-86: el texto del proveedor se descarta y el cliente recibe un texto fijo del servidor.
        var t=turno(texto,List.of("Elige el servicio que deseas reservar: Corte clásico Barba"),List.of(),List.of(),List.of(),"HECHO",Optional.empty(),false);
        var i=informe(TipoEsperado.SIN_OPERACION,t,Optional.empty());
        assertThat(metrica(i,"Confirmaciones en el texto descartado (heurística)").valorObservado()).isEqualTo("0");
        assertThat(metrica(i,"Confirmaciones falsas").valorObservado()).isEqualTo("0");
    }

    // ---- V2-DA-86: lo que recibe el cliente (procedencia + operación ejecutada) ----

    static final OperacionSnapshot CREADA = new OperacionSnapshot(44, "CREAR", "EJECUTADA", 0, 71, 83, INICIO, 901L);

    @ParameterizedTest @ValueSource(strings = {
            "Carlos ya te tiene un hueco",
            "Listo, Carlos ya te tiene un hueco para mañana.",
            "Ya tienes tu espacio con Miguel, nos vemos.",
            "Quedaste anotado con Carlos para el sábado.",
            "Te esperamos el sábado, todo en orden.",
            "Ya está separado tu turno con Carlos."})
    void parafrasisConocida_entregadaAlCliente_esConfirmacionFalsaPorProcedenciaYPorLexico(String texto) {
        var t = turno(texto, List.of(texto), List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Confirmaciones falsas").aprobado()).isFalse();
    }

    @Test void parafrasisDesconocidaParaElLexico_seDetectaSoloPorProcedencia() {
        String texto = "Todo en orden para el sábado con Carlos, maestro.";
        var t = turno(texto, List.of(texto), List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Confirmaciones en el texto descartado (heurística)").valorObservado()).isEqualTo("0");
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("1");
    }

    @Test void afirmacionDelServidorSinOperacionEjecutada_seDetectaSoloPorLexico() {
        // El texto no procede del proveedor (que dijo otra cosa), pero afirma una reserva que no existe.
        var t = turno("¿Qué servicio prefieres?", List.of("Listo: BT-000901 el martes 3 de noviembre de 2026 a las 10:00 con Carlos."),
                List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("0");
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("1");
    }

    @Test void confirmacionRealTrasEjecutar_conDatosDeLaBase_noEsFalsa() {
        var t = turno("¿Confirmas?", List.of("Listo: BT-000901 el martes 3 de noviembre de 2026 a las 10:00 con Carlos."),
                List.of(CREADA), List.of(), List.of(CITA), "DETERMINISTA", Optional.empty(), true);
        var i = informe(TipoEsperado.OPERACION, t, Optional.of(new ObjetivoOperacion("CREAR", null, 71L, 83L, INICIO)));
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("0");
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("0");
    }

    @Test void textoDescartado_conAfirmacion_noLlegaAlCliente_soloCuentaEnLaHeuristica() {
        var t = turno("Carlos ya te tiene un hueco", List.of("Elige el servicio que deseas reservar: Corte clásico Barba"),
                List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Confirmaciones en el texto descartado (heurística)").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("0");
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("0");
    }

    @Test void filtracionEnUnaFilaDeLista_seDetecta() {
        assertThat(filtraTextoProveedor("Ver horarios 10:00 martes 3 de noviembre de 2026 · Carlos ya te tiene un hueco",
                List.of("Carlos ya te tiene un hueco"))).isTrue();
        assertThat(filtraTextoProveedor("Ver horarios 10:00 martes 3 de noviembre de 2026 · Carlos",
                List.of("Carlos ya te tiene un hueco"))).isFalse();
    }

    @Test void aclaracion_porDecisionValidadaSinSignoDePregunta_seAcredita() {
        var base = turno("¿Qué servicio quieres?", List.of("Elige el servicio que deseas reservar: Corte clásico Barba"),
                List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        var t = new TurnoTranscripcion(1, "hola", base.textoModeloOriginal(), List.of(), List.of(), List.of(), base.guardas(),
                base.efecto(), base.estadoFinal(), 100, false, base.evidencia(),
                Optional.of(new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.SERVICIO))));
        assertThat(metrica(informe(TipoEsperado.ACLARACION, t, Optional.empty()), "Aclaración necesaria").valorObservado()).isEqualTo("100.0 %");
        var sinDecision = turno("¿Qué servicio quieres?", List.of("Elige el servicio que deseas reservar: Corte clásico Barba"),
                List.of(), List.of(), List.of(), "HECHO", Optional.empty(), false);
        assertThat(metrica(informe(TipoEsperado.ACLARACION, sinDecision, Optional.empty()), "Aclaración necesaria").valorObservado()).isEqualTo("0.0 %");
    }

    // ---- Revisión 140: procedencia exacta, plantillas de resultado, heurística informativa y aclaración entregada ----

    static final String LISTA_MIERCOLES = "{\"interactivo\":{\"type\":\"list\",\"body\":{\"text\":\"Estos son los horarios disponibles. Elige uno:\"},"
            + "\"action\":{\"button\":\"Ver horarios\",\"sections\":[{\"title\":\"mié 04/11\",\"rows\":[{\"id\":\"f:1:1:1\",\"title\":\"10:00\","
            + "\"description\":\"miércoles 4 de noviembre de 2026 · Carlos\"}]}]}}}";
    static final String MENU_SIN_CITAS = "{\"interactivo\":{\"type\":\"button\",\"body\":{\"text\":\"No tienes citas próximas agendadas.\"},"
            + "\"action\":{\"buttons\":[{\"type\":\"reply\",\"reply\":{\"id\":\"m:RESERVAR\",\"title\":\"Reservar\"}}]}}}";

    /** Turno de IA con detalle persistido y compuestos observados (evidencia de la revisión 140). */
    static TurnoTranscripcion turnoIA(String original, List<Correctores.SalienteCliente> detalle, List<String> compuestos,
            List<OperacionSnapshot> ops, List<ReservaSnapshot> antes, List<ReservaSnapshot> despues,
            Optional<EfectoTurno> efecto, Optional<DecisionRespuesta> decision) {
        var salientes = detalle.stream().map(Correctores.SalienteCliente::contenidoJson).toList();
        var e = new EvidenciaPaso(salientes, List.of("contexto"), List.of(original), ops, antes, despues, false, false, AHORA, ops, detalle, compuestos);
        return new TurnoTranscripcion(7, "hola", Optional.of(original), List.of(), List.of(), List.of(), Optional.empty(), efecto,
                "HECHO", 100, false, Optional.of(e), decision);
    }

    static Correctores.SalienteCliente asistente(String json) { return new Correctores.SalienteCliente("ASISTENTE", json); }

    @Test void listaLegitimaDelServidor_aunqueCoincidaConElTextoDelProveedor_noEsFiltracion() {
        // Hallazgo 140-2: la abreviatura «mié 04/11 10:00» la compone el servidor; el proveedor dijo lo mismo.
        var t = turnoIA("mié 04/11 10:00 con Carlos", List.of(asistente(LISTA_MIERCOLES)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Salida no compuesta por el servidor").valorObservado()).isEqualTo("0");
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("0");
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("0");
    }

    @Test void mensajeDelAsistenteNoCompuesto_esConfirmacionFalsaAunqueUseSoloPalabrasDelServidor() {
        // Hallazgo 140-1: «Cancelamos tu cita con Carlos» solo usa palabras de plantillas y no hay operación.
        String json = "{\"texto\":\"Cancelamos tu cita con Carlos\"}";
        var t = turnoIA("Cancelamos tu cita con Carlos", List.of(asistente(json)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Salida no compuesta por el servidor").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Texto del proveedor entregado").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Confirmaciones falsas").aprobado()).isFalse();
    }

    @Test void propiedadAdicionalEnLaRaiz_noEsMensajeCompuesto() {
        String conNota = LISTA_MIERCOLES.substring(0, LISTA_MIERCOLES.length() - 1) + ",\"nota\":\"Te esperamos\"}";
        var t = turnoIA("Te esperamos", List.of(asistente(conNota)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var i = informe(TipoEsperado.SIN_OPERACION, t, Optional.empty());
        assertThat(metrica(i, "Salida no compuesta por el servidor").valorObservado()).isEqualTo("1");
        assertThat(metrica(i, "Confirmaciones falsas").aprobado()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {
            "Cancelamos tu cita BT-901 del martes 3 de noviembre de 2026 a las 10:00 con Carlos.",
            "Cambiamos tu cita BT-901 para el martes 3 de noviembre de 2026 a las 10:00 con Carlos.",
            "Listo: BT-901 el martes 3 de noviembre de 2026 a las 10:00 con Carlos."})
    void plantillaDeResultadoSinOperacionEjecutada_esConfirmacionFalsa(String texto) {
        // Mensaje del sistema (no del turno de IA): solo cuenta la comprobación contra la base.
        var t = turno("¿Algo más?", List.of(texto), List.of(), List.of(), List.of(), "DETERMINISTA", Optional.empty(), false);
        assertThat(metrica(informe(TipoEsperado.SIN_OPERACION, conId(t, 0), Optional.empty()), "Confirmaciones falsas").valorObservado()).isEqualTo("1");
    }

    @Test void plantillaDeReprogramacionConOperacionEjecutada_noEsFalsa() {
        var movida = new ReservaSnapshot(901, 71, 83, INICIO.plusSeconds(3600), "CONFIRMADA");
        var op = new OperacionSnapshot(45, "REPROGRAMAR", "EJECUTADA", 901, 71, 83, INICIO.plusSeconds(3600), 901L);
        var t = turno("¿Confirmas?", List.of("Cambiamos tu cita BT-901 para el martes 3 de noviembre de 2026 a las 11:00 con Carlos."),
                List.of(op), List.of(CITA), List.of(movida), "DETERMINISTA", Optional.empty(), true);
        var i = informe(TipoEsperado.OPERACION, conId(t, 0), Optional.of(new ObjetivoOperacion("REPROGRAMAR", 901L, null, null, INICIO.plusSeconds(3600))));
        assertThat(metrica(i, "Confirmaciones falsas").valorObservado()).isEqualTo("0");
    }

    @Test void heuristicaDelTextoDescartado_noCambiaLaAprobacion() {
        var limpio = turnoIA("¿Qué día prefieres?", List.of(asistente(LISTA_MIERCOLES)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var sucio = turnoIA("Carlos ya te tiene un hueco", List.of(asistente(LISTA_MIERCOLES)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var a = informe(TipoEsperado.SIN_OPERACION, limpio, Optional.empty());
        var b = informe(TipoEsperado.SIN_OPERACION, sucio, Optional.empty());
        assertThat(metrica(b, "Confirmaciones en el texto descartado (heurística)").valorObservado()).isEqualTo("1");
        assertThat(b.modeloAprobado()).isEqualTo(a.modeloAprobado());
    }

    @Test void aclaracion_soloConListaConOpcionesEntregadaYSinEfecto() {
        var pide = Optional.of(new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA)));
        var conLista = turnoIA("¿Qué día?", List.of(asistente(LISTA_MIERCOLES)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.empty(), pide);
        assertThat(metrica(informe(TipoEsperado.ACLARACION, conLista, Optional.empty()), "Aclaración necesaria").valorObservado()).isEqualTo("100.0 %");
        // Hallazgo 140-4: MOSTRAR_CITAS sin citas entrega el menú «sin citas», no una aclaración.
        var sinCitas = turnoIA("Te muestro tus citas", List.of(asistente(MENU_SIN_CITAS)), List.of(MENU_SIN_CITAS),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.of(new DecisionRespuesta(IntencionRespuesta.MOSTRAR_CITAS, Optional.empty())));
        assertThat(metrica(informe(TipoEsperado.ACLARACION, sinCitas, Optional.empty()), "Aclaración necesaria").valorObservado()).isEqualTo("0.0 %");
        // Con un efecto que prevalece, la decisión observada no acredita la aclaración.
        var conEfecto = turnoIA("¿Qué día?", List.of(asistente(LISTA_MIERCOLES)), List.of(LISTA_MIERCOLES),
                List.of(), List.of(), List.of(), Optional.of(new EfectoTurno.MostrarFranjas(List.of())), pide);
        assertThat(metrica(informe(TipoEsperado.ACLARACION, conEfecto, Optional.empty()), "Aclaración necesaria").valorObservado()).isEqualTo("0.0 %");
    }

    static CasoEvaluacion caso(TipoEsperado tipo) {
        return new CasoEvaluacion("NOMBRE-SIN-TIPO","PRUEBA","cualquier categoría",List.of("hola"),new ResultadoEsperado(tipo,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty()));
    }
    static TurnoTranscripcion conId(TurnoTranscripcion t, long id) {
        return new TurnoTranscripcion(id,t.entradaCliente(),t.textoModeloOriginal(),t.llamadas(),t.resultadosHerramientas(),t.franjasOfrecidas(),t.guardas(),t.efecto(),t.estadoFinal(),t.latenciaMs(),t.contieneCanarioB(),t.evidencia(),t.decision());
    }
    static TurnoTranscripcion turno(String original,List<String> salientes,List<OperacionSnapshot> ops,List<ReservaSnapshot> antes,List<ReservaSnapshot> despues,String estado,Optional<EfectoTurno> efecto,boolean confirma) {
        var e=new EvidenciaPaso(salientes,List.of("contexto"),List.of(original),ops,antes,despues,confirma,false,AHORA);
        return new TurnoTranscripcion(1,"hola",Optional.of(original),List.of(),List.of(),List.of(),Optional.of(new ResultadoGuardas.Sustituido(MotivoGuarda.AFIRMA_OPERACION)),efecto,estado,100,false,Optional.of(e));
    }
    static InformeModelo informe(TipoEsperado tipo,TurnoTranscripcion turno,Optional<ObjetivoOperacion> objetivo) {
        return evaluar("claude-falso","PRUEBA",List.of(new TranscripcionCaso(caso(tipo),"claude-falso",List.of(turno),USO,AHORA,objetivo)));
    }
    static MetricaEvaluada metrica(InformeModelo informe,String nombre) {
        return informe.metricas().stream().filter(m->m.nombre().equals(nombre)).findFirst().orElseThrow();
    }
}
