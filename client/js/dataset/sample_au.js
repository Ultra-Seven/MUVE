import _ from "underscore";

class Sample_AU {
    constructor() {
        this.columns = [
            "Unique Key", "Data Source", "Data Source Research Date", "SP Next", "Sp Next 1",
            "Data Tool", "Unnamed: 5", "Organisation Name", "Website",
            "First Name", "Middle Name", "Last Name", "Job Name",
            "Department", "Authority Level", "email_work", "Email Status",
            "Switch phone", "Linkedin", "Switch Phone 2", "Direct Phone", "Direct Phone 2",
            "Personal Phone", "Personal Phone 2", "Address 1", "Address 2", "Address 3",
            "City", "State", "Post Code", "Mailing Country", "Industry Name", "Company Size",
            "Company Size 1", "Company Range", "Company Range 1", "Company Linkedin", "Revenue",
            "Revenue 1", "Tele-verified", "Batch", "Filename", "Batch Name", "Unnamed: 42", "Unnamed: 43"
        ];
        this.samples = [
            "How many emails in CN?"
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
export default Sample_AU;