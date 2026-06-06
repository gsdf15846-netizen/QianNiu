package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Character {
    private String id;
    private String name;
    private List<String> aliases;
    private String description;
}
