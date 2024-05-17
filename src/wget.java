import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class wget {
    
    private static final int MAX_THREADS = 10; // Máximo de hilos permitidos
    private static final String OUTPUT_FOLDER = System.getProperty("user.dir");; // Carpeta de salida para guardar los archivos
    private static final Set<String> visitedPages = new HashSet<>(); // Páginas visitadas
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(); // Lock para acceso concurrente a la lista de páginas visitadas

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Ingrese el comando (formato: wget <nivel de profundidad> <url>):");
        String input = scanner.nextLine();
        String[] command = input.split(" ");

        if (command.length != 3 || !command[0].equals("wget")) {
            System.out.println("Comando inválido. Por favor, use el formato: wget <nivel de profundidad> <url>");
            return;
        }

        String depthInput = command[1];
        String startUrl = command[2];
        int maxDepth;

        if (depthInput.equals("-r")) {
            maxDepth = Integer.MAX_VALUE;
        } else {
            try {
                maxDepth = Integer.parseInt(depthInput);
            } catch (NumberFormatException e) {
                System.out.println("Nivel de profundidad inválido. Debe ser un número entero o '-r' para recursivo.");
                return;
            }
        }

        //creacion de la alberca de hilos
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        crawl(startUrl, 0, OUTPUT_FOLDER, maxDepth, executor);
        executor.shutdown();
        
        System.out.println("\nPáginas visitadas:");
        for (String page : visitedPages) {
            System.out.println(page);
        }
    }

    private static void crawl(String url, int depth, String FOLDER, int MAX_DEPTH, ExecutorService executor) {
        if (depth > MAX_DEPTH) {
            return;
        }

        lock.readLock().lock(); // Bloqueo de lectura para verificar si la página ya ha sido visitada
        boolean alreadyVisited = visitedPages.contains(url);
        lock.readLock().unlock();

        if (!alreadyVisited) {
            lock.writeLock().lock(); // Bloqueo de escritura para añadir la página a la lista de visitadas
            visitedPages.add(url);
            lock.writeLock().unlock();

            try {
                // Descargar la página
                Document doc = Jsoup.connect(url).timeout(10 * 1000).get();

                // Imprimir el HTML de la página
                System.out.println("HTML de la página: " + url);
              //  System.out.println(doc.outerHtml());
              
              // Crear una carpeta para guardar los archivos de esta página
                String folderName = FOLDER + "/" + getFolderNameFromUrl(url);
                File folder = new File(folderName);
                folder.mkdirs();

                // Tokenizar los enlaces <a> y mostrarlos
                // Encontrar enlaces y descargar páginas en hilos separados
                Elements links = doc.select("a[href]");
                System.out.println("\nEnlaces encontrados en la página:");
                for (Element link : links) {
                    String href = link.absUrl("href");
                    System.out.println("enlace encontrado: " + href);

                    // Verificar si el enlace termina con una extensión de archivo
                    if (href.matches(".*\\.[a-zA-Z0-9]{1,5}$")) {
                        // Descargar documento y guardarlo
                        downloadDocument(folderName, href);
                    } else {
                        // Verificar si el enlace es válido y no está en el patrón no deseado
                        if (!href.matches(".*\\?C=(N|M|S);O=(D|A)$")) {                            
                            executor.submit(() -> crawl(href, depth + 1, folderName, MAX_DEPTH, executor)); // Enviar hilo para descargar la nueva página
                        } else {
                            System.out.println("entro en pagina no deseada");
                        }
                    }
                }
               /* for (Element link : links) {
                    String nextUrl = link.absUrl("href");
                    System.out.println("enlace actual antes de mandar el hilo: " + nextUrl);
                    if (!nextUrl.matches(".*\\?C=(N|M|S);O=(D|A)$")) { // Filtrar enlaces con patrón '?C=N;O=D', '?C=M;O=A', '?C=S;O=A'
                        executor.submit(() -> crawl(nextUrl, depth + 1));
                    } else {
                        System.out.println("entro en pagina no deseada");
                    }
                    //executor.submit(() -> crawl(nextUrl, depth + 1));
                }*/
                executor.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Ya se visitó esta página");
        }
                
    }
    private static void downloadDocument(String folder, String url) {
        try {
            URL documentUrl = new URL(url);
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            System.out.println("Nombre del archivo: " + fileName);
            File outputFile = new File(folder, fileName);

            try (InputStream inputStream = documentUrl.openStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[100000];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    //System.out.println("bytes " + buffer);
                    outputStream.write(buffer, 0, bytesRead);
                }
                System.out.println("Documento descargado y guardado: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                //e.printStackTrace();
                System.out.println("No se guardó este archivo, es enlace");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static String getFolderNameFromUrl(String url) {
        String folderName = url.substring(url.lastIndexOf('/') + 1);
        if (folderName.isEmpty() || folderName.endsWith("/")) {
            int secondLastSlashIndex = url.lastIndexOf('/', url.lastIndexOf('/') - 1);
            folderName = url.substring(secondLastSlashIndex + 1, url.lastIndexOf('/'));
        }
        return folderName;
    }
}
