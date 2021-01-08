namespace io.neonbee.test3;

service TestService3 {
    entity TestCars {
        key ID : String;
        name : String not null;
        description : String;
    }
}