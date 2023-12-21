package com.github.gpluscb.toni.toni_api.model;

public record Rating(double rating, double deviation, double volatility) {
    public String display() {
        return String.format("%.2f±%.2f, σ=%.2f", rating, deviation * 2, volatility);
    }
}
