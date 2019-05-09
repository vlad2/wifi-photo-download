package ro.vdin.wifiphotodl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties("appconfig")
public class AppConfig {
    private String baseUrl;
    
    private Integer lib;

    private Integer startIndex;
    
    private Integer endIndex;

    private Integer selectionSize;

    private String saveDir;

    public Integer getLib() {
        return lib;
    }

    public void setLib(Integer lib) {
        this.lib = lib;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Integer getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(Integer endIndex) {
        this.endIndex = endIndex;
    }

    public Integer getSelectionSize() {
        return selectionSize;
    }

    public void setSelectionSize(Integer selectionSize) {
        this.selectionSize = selectionSize;
    }

    public String getSaveDir() {
        return saveDir;
    }

    public void setSaveDir(String saveDir) {
        this.saveDir = saveDir;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

}
