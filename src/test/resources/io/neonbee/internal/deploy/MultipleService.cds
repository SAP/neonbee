// You can find the reference of all Core Data Service (CDS) concepts as well as the CDS Definition Language (CDL)
// features here: https://cap.cloud.sap/docs/cds/cdl

namespace io.neonbee.deploymultiple;

service UserService {

    entity Users {
        key ID : String;
        name : String not null;
        description : String;
    }
}

service CarService {

    entity Carss {
        key ID : String;
        make : String not null;
        year : Integer;
    }
}