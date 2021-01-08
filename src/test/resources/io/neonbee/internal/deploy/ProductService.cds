namespace io.neonbee.deploy;

service ProductService {
    entity Products {
        key ID : String;
        name : String not null;
        description : String;
    }
}