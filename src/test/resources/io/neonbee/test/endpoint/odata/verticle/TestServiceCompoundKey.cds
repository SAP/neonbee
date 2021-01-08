namespace io.neonbee.compoundkey;

service TestServiceCompoundKey {
    entity TestCars {
        key ID : String;
        key date : Date;
        name : String not null;
        description : String;
    }
}