/**
 * Javadoc sin LOC.
 */
class Casos {
    String ruta = "/v3/api-docs/**";
    String url = "http://localhost:8080"; // comentario
    String escape = "comilla \" // /* sigue literal";
    char comilla = '\''; /* comentario */
    String texto = """
        // literal
        /* literal

        */ literal
        \""" tampoco cierra
        """;
    int uno = 1; /* bloque
       multilínea */ int dos = 2;
    // sin LOC
}
