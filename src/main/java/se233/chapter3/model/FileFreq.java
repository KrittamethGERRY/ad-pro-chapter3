package se233.chapter3.model;

public class FileFreq {
    private String name, path;
    private Integer freq;
    public FileFreq(String name, String path, Integer freq) {
        this.name = name;
        this.path = path;
        this.freq = freq;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public Integer getFreq() {
        return freq;
    }

    @Override
    public String toString() {
        return String.format("(%s:%d", name, freq);
    }
}
