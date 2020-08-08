package com.lambdajavablockchain.model;

import javax.validation.constraints.NotNull;

/**
 * Car object used by Fabcar chaincode
 */

public class Car {
    @NotNull
    private String id;
    @NotNull
    private String make;
    @NotNull
    private String model;
    @NotNull
    private String colour;
    @NotNull
    private String owner;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return "Car{" +
                "id='" + id + '\'' +
                ", make='" + make + '\'' +
                ", model='" + model + '\'' +
                ", colour='" + colour + '\'' +
                ", owner='" + owner + '\'' +
                '}';
    }
}

