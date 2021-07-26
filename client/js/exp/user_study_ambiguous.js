import Connector from "../engine/connector";

class AmbiguousStudy {
    constructor(urlParams, engine, config) {
        // $("#result_body").show();
        const dataset = "sample_311"
        const element = $('#datasets').children().eq(dataset);
        $(element).prop('selected',true)
            .trigger('change');

        $("#datasets").prop('disabled', 'disabled');
        $("#planner").prop("disabled", 'disabled');
        $("#present").prop("disabled", 'disabled');

        // $("#btn-start-recording").hide();
        $("#btn-dev").hide();
        $("#spoken-text").prop("disabled", true);
        // $("#btn-dev").click(() => {
        //     window.history.back();
        // });
        // $("#btn-submit").hide();

        // Simulate click the submit button
        this.shownStart = Date.now();
        this.engine = engine;
        const params = {
            sender: "AJAX",
            callback: () => {
                $("#btn-dev").click();
            },
            url: config.host + "/study"
        };
        this.querySender = new Connector(params);
    }

    send(results) {
        const renderEnd = this.engine.renderTime;
        const shown = renderEnd - this.shownStart;
        const respond = this.engine.end - renderEnd;
        if (isNaN(respond) || isNaN(shown)) {
            return;
        }
        const dataset = $('#datasets').val();
        const message = [this.user, this.level, this.method, dataset, this.qid, results,
            shown, respond, shown + respond];
        this.querySender.send(message.join("|"));
    }
}
export default AmbiguousStudy;