import _ from "underscore";

class Customer {
    constructor() {
        this.columns = [
            "Customer", "Income"
        ];
        this.samples = [
            "What's income of customer Laura?",
        ];
    }

    setup() {
        $("#columns").empty();
        $("#queries").empty();
        _.each(this.columns, column => {
            $("#columns").append("<li>" + column + "</li>")
        })

        _.each(this.samples, sample => {
            $("#queries").append("<li>" + sample + "</li>")
        })
    }
}
export default Customer;