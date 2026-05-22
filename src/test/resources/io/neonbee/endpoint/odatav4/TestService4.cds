namespace test;

@neonbee.endpoint: 'odataproxy'
service Service {
    entity TestEntity {
        key ID : String;
        name : String not null;
        description : String;
    }
}