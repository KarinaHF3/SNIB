package snib;

import java.nio.file.Path;

/** Métricas de una ejecución completa. */
public class Resultado {
    private final long leidos, aceptados, rechazados, errores, tiempoNanos;
    private final Path salida, log;

    public Resultado(long leidos, long aceptados, long rechazados, long errores,
                     long tiempoNanos, Path salida, Path log) {
        this.leidos = leidos;
        this.aceptados = aceptados;
        this.rechazados = rechazados;
        this.errores = errores;
        this.tiempoNanos = tiempoNanos;
        this.salida = salida;
        this.log = log;
    }
    public long getLeidos() { return leidos; }
    public long getAceptados() { return aceptados; }
    public long getRechazados() { return rechazados; }
    public long getErrores() { return errores; }
    public long getTiempoNanos() { return tiempoNanos; }
    public Path getSalida() { return salida; }
    public Path getLog() { return log; }
    public double getTiempoSegundos() { return tiempoNanos / 1_000_000_000.0; }
    public double getRegistrosPorSegundo() {
        return tiempoNanos > 0 ? leidos / getTiempoSegundos() : 0;
    }
}