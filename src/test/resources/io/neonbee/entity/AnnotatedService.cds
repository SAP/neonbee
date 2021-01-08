namespace AnnotatedService;

service Service {
    entity LordCitrange as projection on AnnotatedService.LordCitrange;
    entity Hodor as projection on AnnotatedService.Hodor;
}

// Necessary for the scenario that an entity is annotated
@Common.customEntityTerm.customEntityProperty: 'customValue'
entity LordCitrange {
    // Necessary for the scenario that an annotation has a String value
    // Necessary for the scenario that a property is annotated
    @Common.customPropertyTerm.customPropertyValueString: 'StringValue' key name : String(10);

    // Necessary for the scenario that an annotation has an integer value
    @Common.customPropertyTerm.customPropertyValueInteger: 3 dkp : Integer;
}

// Necessary for the scenario that one property has multiple records
entity Hodor {
    @Common.customPropertyTerm.customPropertyValue1: 'Value1'
    @Common.customPropertyTerm.customPropertyValue2: 'Value2'
    key hodor : String(10);
}