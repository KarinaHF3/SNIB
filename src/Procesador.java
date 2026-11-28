package snib;

import java.nio.file.Path;
import java.util.List;
import java.io.IOException;

/** Contrato compartido por las dos implementaciones. Índices desde cero. */
public interface Procesador {
    Resultado procesar(Path entrada, Path salida, Path log,
                        List<Integer> columnas, int columnaFiltro,
                        String valorFiltro, int hilos)
            throws IOException, InterruptedException;
}