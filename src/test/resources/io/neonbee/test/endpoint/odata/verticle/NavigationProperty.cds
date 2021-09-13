namespace io.neonbee.test;

service NavProps {
    entity Products {
        key ID : Integer;
        name : String;
        category : Association to Categories;
    }

    entity Categories {
        key ID : Integer;
        name : String;
        products: Association to many Products on products.category = $self;
    }
}

