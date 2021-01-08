// You can find the reference of all Core Data Service (CDS) concepts as well as the CDS Definition Language (CDL)
// features here: https://cap.cloud.sap/docs/cds/cdl

namespace io.neonbee.handler2;

// CDS allows to define service interfaces as collections of exposed entities enclosed in a service block
service Test2Service {

    entity TestUsers {
        key ID : String;
        name : String not null;
        description : String;
    }

}