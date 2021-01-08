// You can find the reference of all Core Data Service (CDS) concepts as well as the CDS Definition Language (CDL)
// features here: https://cap.cloud.sap/docs/cds/cdl

namespace io.neonbee.reference;

service ReferenceService {

    // this annotation will add a Reference to the parsed edmx file with a cds-compiler with the version 1.30.0+
    @Common.customEntityTerm.customEntityProperty: 'customValue'
    entity TestProducts {
        @Common.customPropertyTerm.customPropertyValueString: 'StringValue' key name : String(10);
    }
}