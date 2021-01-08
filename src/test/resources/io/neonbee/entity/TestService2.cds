// You can find the reference of all Core Data Service (CDS) concepts as well as the CDS Definition Language (CDL)
// features here: https://cap.cloud.sap/docs/cds/cdl

namespace io.neonbee.test2;

service TestService2Users {

    entity TestUsers {
        key ID : String;
        name : String not null;
        description : String;
    }
}

service TestService2Cars {

    entity TestCars {
        key ID : String;
        make : String not null;
        year : Integer;
    }
}