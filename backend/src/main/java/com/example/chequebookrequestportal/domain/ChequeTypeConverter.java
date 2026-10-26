package com.example.chequebookrequestportal.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ChequeTypeConverter implements AttributeConverter<ChequeType, String> {
    @Override
    public String convertToDatabaseColumn(ChequeType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ChequeType convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : ChequeType.fromCode(databaseValue);
    }
}
