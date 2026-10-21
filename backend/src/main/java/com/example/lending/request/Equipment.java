package com.example.lending.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 備品。アプリからは読み取りだけ（行は Flyway の V2 が入れる）。 */
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @Column(name = "equipment_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    protected Equipment() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
