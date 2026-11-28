package snib;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class VentanaPrincipal extends JFrame {
    private final JTextField ruta = new JTextField();
    private final JButton examinar = new JButton("Examinar...");
    private final JPanel columnas = new JPanel();
    private final List<JCheckBox> casillas = new ArrayList<>();
    private final JComboBox<String> filtro = new JComboBox<>();
    private final JTextField valor = new JTextField();
    private final JComboBox<String> modo = new JComboBox<>(new String[]{"Secuencial", "Concurrente"});
    private final JSpinner hilos = new JSpinner(new SpinnerNumberModel(
            Runtime.getRuntime().availableProcessors(), 1, Integer.MAX_VALUE, 1));
    private final JButton procesar = new JButton("Procesar");
    private final JTextArea mensajes = new JTextArea(10, 45);
    private Path entrada;
    private final Procesador secuencial, concurrente;

    // Permite probar la ventana antes de integrar los motores.
    public VentanaPrincipal() { this(null, null); }

    public VentanaPrincipal(Procesador secuencial, Procesador concurrente) {
        super("SNIB Explorer");
        this.secuencial = secuencial;
        this.concurrente = concurrente;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel principal = new JPanel();
        principal.setLayout(new BoxLayout(principal, BoxLayout.Y_AXIS));
        principal.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        ruta.setEditable(false);
        JPanel archivo = new JPanel(new BorderLayout(8, 0));
        archivo.setBorder(BorderFactory.createTitledBorder("Archivo CSV"));
        archivo.add(ruta, BorderLayout.CENTER);
        archivo.add(examinar, BorderLayout.EAST);
        principal.add(archivo);
        columnas.setLayout(new BoxLayout(columnas, BoxLayout.Y_AXIS));
        JScrollPane lista = new JScrollPane(columnas);
        lista.setPreferredSize(new Dimension(600, 180));
        lista.setBorder(BorderFactory.createTitledBorder("Columnas de salida"));
        principal.add(lista);
        JPanel opciones = new JPanel(new GridLayout(4, 2, 8, 5));
        opciones.setBorder(BorderFactory.createTitledBorder("Consulta"));
        opciones.add(new JLabel("Columna del filtro:")); opciones.add(filtro);
        opciones.add(new JLabel("Valor (igualdad):")); opciones.add(valor);
        opciones.add(new JLabel("Modo:")); opciones.add(modo);
        opciones.add(new JLabel("Hilos:")); opciones.add(hilos);
        principal.add(opciones);
        principal.add(procesar);
        mensajes.setEditable(false);
        JScrollPane consola = new JScrollPane(mensajes);
        consola.setBorder(BorderFactory.createTitledBorder("Mensajes"));
        principal.add(consola);
        examinar.addActionListener(e -> seleccionarArchivo());
        procesar.addActionListener(e -> ejecutar());
        modo.addActionListener(e -> hilos.setEnabled(modo.getSelectedIndex() == 1));
        hilos.setEnabled(false);
        procesar.setEnabled(false);
        setContentPane(principal);
        pack();
        setLocationRelativeTo(null);
    }

    private JFileChooser selectorCSV() {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));
        return selector;
    }

    private void seleccionarArchivo() {
        JFileChooser selector = selectorCSV();
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path candidato = selector.getSelectedFile().toPath();
        try {
            List<String> nombres = leerEncabezados(candidato);
            entrada = candidato;
            ruta.setText(entrada.toString());
            columnas.removeAll(); casillas.clear(); filtro.removeAllItems();
            for (int i = 0; i < nombres.size(); i++) {
                String etiqueta = (i + 1) + ". " + nombres.get(i);
                JCheckBox casilla = new JCheckBox(etiqueta, true);
                casillas.add(casilla); columnas.add(casilla); filtro.addItem(etiqueta);
            }
            columnas.revalidate(); columnas.repaint();
            procesar.setEnabled(true);
            mensaje("Archivo seleccionado: " + entrada + " | Columnas: " + nombres.size());
        } catch (IOException ex) { mensaje("Error: " + ex.getMessage()); }
    }

    // Lee solo el primer registro CSV; admite comillas y campos multilínea.
    static List<String> leerEncabezados(Path archivo) throws IOException {
        try (PushbackReader lector = new PushbackReader(Files.newBufferedReader(archivo, StandardCharsets.UTF_8), 1)) {
            List<String> nombres = new ArrayList<>();
            StringBuilder campo = new StringBuilder();
            boolean comillas = false, cerrado = false, hayDatos = false;
            int c = lector.read();
            if (c == 0xFEFF) c = lector.read(); // BOM UTF-8
            while (c != -1) {
                hayDatos = true;
                if (comillas) {
                    if (c == '"') {
                        int siguiente = lector.read();
                        if (siguiente == '"') campo.append('"');
                        else { comillas = false; cerrado = true; if (siguiente != -1) lector.unread(siguiente); }
                    } else campo.append((char)c);
                } else if (c == ',' || c == '\n' || c == '\r') {
                    nombres.add(campo.toString()); campo.setLength(0); cerrado = false;
                    if (c != ',') return validarEncabezados(nombres);
                } else if (c == '"' && campo.length() == 0 && !cerrado) comillas = true;
                else {
                    if (cerrado || c == '"') throw new IOException("Encabezado CSV malformado");
                    campo.append((char)c);
                }
                c = lector.read();
            }
            if (!hayDatos || comillas) throw new IOException("Encabezado vacío o incompleto");
            nombres.add(campo.toString());
            return validarEncabezados(nombres);
        }
    }
    private static List<String> validarEncabezados(List<String> nombres) throws IOException {
        if (nombres.size() == 1 && nombres.get(0).isBlank()) throw new IOException("Encabezado vacío");
        return nombres;
    }

    private void ejecutar() {
        List<Integer> seleccion = new ArrayList<>();
        for (int i = 0; i < casillas.size(); i++) if (casillas.get(i).isSelected()) seleccion.add(i);
        if (seleccion.isEmpty()) { mensaje("Selecciona al menos una columna."); return; }
        Procesador motor = modo.getSelectedIndex() == 0 ? secuencial : concurrente;
        if (motor == null) { mensaje("Modo aún no integrado. Columnas: " + seleccion + "; filtro: " + filtro.getSelectedItem() + " = " + valor.getText()); return; }
        JFileChooser selector = selectorCSV();
        selector.setSelectedFile(new File("resultado.csv"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path salida = selector.getSelectedFile().toPath().toAbsolutePath().normalize();
        Path origen = entrada.toAbsolutePath().normalize();
        Path log = salida.resolveSibling(salida.getFileName() + ".errores.txt");
        try {
            if (origen.equals(salida) || origen.equals(log) ||
                (Files.exists(salida) && Files.isSameFile(origen, salida)) ||
                (Files.exists(log) && Files.isSameFile(origen, log)) ||
                (Files.exists(salida) && Files.exists(log) && Files.isSameFile(salida, log))) {
                mensaje("Las rutas de entrada, salida y log deben ser distintas."); return;
            }
        } catch (IOException ex) { mensaje("Error al validar rutas: " + ex.getMessage()); return; }
        if ((Files.exists(salida) || Files.exists(log)) && JOptionPane.showConfirmDialog(this,
                "¿Sobrescribir la salida y/o el log existentes?", "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        int indice = filtro.getSelectedIndex();
        String buscado = valor.getText();
        int cantidad = modo.getSelectedIndex() == 0 ? 1 : (Integer) hilos.getValue();
        habilitar(false);
        mensaje("Procesando archivo...");
        new SwingWorker<Resultado, Void>() {
            @Override protected Resultado doInBackground() throws Exception {
                return motor.procesar(origen, salida, log, List.copyOf(seleccion), indice, buscado, cantidad);
            }
            @Override protected void done() {
                try { mostrarResultado(get()); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); mensaje("Ejecución interrumpida."); }
                catch (ExecutionException ex) { mensaje("Error: " + ex.getCause()); }
                finally { habilitar(true); }
            }
        }.execute();
    }
    private void habilitar(boolean activo) {
        examinar.setEnabled(activo); procesar.setEnabled(activo); filtro.setEnabled(activo);
        valor.setEnabled(activo); modo.setEnabled(activo);
        hilos.setEnabled(activo && modo.getSelectedIndex() == 1);
        for (JCheckBox casilla : casillas) casilla.setEnabled(activo);
        setDefaultCloseOperation(activo ? EXIT_ON_CLOSE : DO_NOTHING_ON_CLOSE);
    }
    private void mostrarResultado(Resultado r) {
        mensaje("Leídos: " + r.getLeidos()); mensaje("Aceptados: " + r.getAceptados());
        mensaje("Rechazados: " + r.getRechazados()); mensaje("Errores: " + r.getErrores());
        mensaje(String.format("Tiempo: %.3f s | Registros/s: %.2f", r.getTiempoSegundos(), r.getRegistrosPorSegundo()));
        mensaje("Salida: " + r.getSalida()); mensaje("Log: " + r.getLog());
    }
    private void mensaje(String texto) {
        mensajes.append(texto + "\n"); mensajes.setCaretPosition(mensajes.getDocument().getLength());
    }
}
