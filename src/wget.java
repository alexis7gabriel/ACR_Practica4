import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Wget {
    
    private static final int MAX_THREADS = 1; // Máximo de hilos permitidos
    private static final String OUTPUT_FOLDER = "C:\\Users\\cesar\\Desktop\\ACR\\Practica4\\Practica4\\Archivos Locales"; // Carpeta de salida para guardar los archivos
    private static final int MAX_DEPTH = 0; // Máxima profundidad de búsqueda
    private static final Set<String> visitedPages = new HashSet<>(); // Páginas visitadas
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(); // Lock para acceso concurrente a la lista de páginas visitadas

    public static void main(String[] args) {
        String startUrl = "http://148.204.58.221/axel/aplicaciones/sockets/java/servidor/"; // URL inicial
        crawl(startUrl, 0);
    }

    private static void crawl(String url, int depth) {
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
                Document doc = Jsoup.connect(url).get();

                // Imprimir el HTML de la página
                System.out.println("HTML de la página: " + url);
              //  System.out.println(doc.outerHtml());

                // Tokenizar los enlaces <a> y mostrarlos
                Elements links = doc.select("a[href]");
                System.out.println("\nEnlaces encontrados en la página:");
                for (Element link : links) {
                    String href = link.absUrl("href");
                    System.out.println(href);
                  //  if (href.toLowerCase().endsWith(".pdf") || href.toLowerCase().endsWith(".java") || href.toLowerCase().endsWith(".txt")) {
                        // Descargar documento y guardarlo
                        downloadDocument(href);
                   // }
                }

                // Encontrar enlaces y descargar páginas en hilos separados
                ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
                for (Element link : links) {
                    String nextUrl = link.absUrl("href");
                    executor.submit(() -> crawl(nextUrl, depth + 1));
                }
                executor.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private static void downloadDocument(String url) {
        try {
            URL documentUrl = new URL(url);
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            System.out.println("Nombre del archivo: " + fileName);
            File outputFile = new File(OUTPUT_FOLDER, fileName);

            try (InputStream inputStream = documentUrl.openStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[100000];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    System.out.println("bytes " + buffer);
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
}
