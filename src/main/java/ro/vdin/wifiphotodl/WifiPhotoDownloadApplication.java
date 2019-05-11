package ro.vdin.wifiphotodl;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WifiPhotoDownloadApplication implements CommandLineRunner {
    private Downloader downloader;

    public WifiPhotoDownloadApplication(Downloader downloader) {
        this.downloader = downloader;
    }

    public static void main(String[] args) {
        SpringApplication.run(WifiPhotoDownloadApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        downloader.download();
    }
}
