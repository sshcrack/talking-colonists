package me.sshcrack.mc_talking.gson.properties;

public abstract class Property {
    private String type;
    private transient boolean isRequired = false;

    public Property(String type) {
        this.type = type;
    }

    public Property(String type, boolean required) {
        this.type = type;
        this.isRequired = required;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public void setRequired(boolean required) {
        this.isRequired = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
