package com.sudhar.craze.connectivity;

public enum ClientID {

    MAIN(100),
    CAM(101),
    DEPTH(102);

    private int value;

    ClientID(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
