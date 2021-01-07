import Connector from "../engine/connector";

class Study {
    constructor(urlParams, engine, config) {
        $("#result_body").show();
        $("#result_box").change(function() {
            if(this.checked) {
                $("#result-text").val('');
                $("#result-text").prop("disabled", true);
            }
            else {
                $("#result-text").prop("disabled", false);
            }
        });

        const explain = urlParams.get('explain');
        const sql = urlParams.get('sql');
        const dataset = urlParams.get('dataset');
        const user = urlParams.get('user');
        this.user = user;
        this.dataset = dataset;
        this.qid = parseInt(urlParams.get('qid'));
        this.explain = explain;
        this.method = document.getElementById("planner") == null ? 0 : 1;

        const element = $('#datasets').children().eq(dataset);
        $(element).prop('selected',true)
            .trigger('change');

        $("#datasets").prop('disabled', 'disabled');
        $("#planner").prop("disabled", 'disabled');

        $("#spoken-text").val(explain);
        $("#btn-start-recording").hide();
        $("#btn-dev").html("Go back");
        $("#btn-dev").click(() => {
            window.history.back();
        });
        $("#btn-submit").hide();
        $("#spoken-text").prop("disabled", true);
        $("#spoken-text").css("color","black");
        $("#spoken-text").css('font-weight', 'bold');

        // Simulate click the submit button
        this.shownStart = Date.now();
        let finishedQuery = JSON.parse(sessionStorage.getItem("finished") ||
            "[[[], [], []], [[], [], []]]");
        engine.simulate(sql);
        this.engine = engine;
        const params = {
            sender: "AJAX",
            callback: () => {
                finishedQuery[this.method][dataset].push(this.qid);
                sessionStorage.setItem("finished", JSON.stringify(finishedQuery));
                $("#btn-dev").click();
            },
            url: config.host + "/study"
        };
        this.querySender = new Connector(params);
    }

    send(results) {
        const renderEnd = this.engine.renderTime;
        const shown = renderEnd - this.shownStart;
        const respond = Date.now() - renderEnd;
        if (isNaN(respond) || isNaN(shown)) {
            return;
        }
        const dataset = $('#datasets').val();
        const message = [this.user, this.method, dataset, this.qid, results,
            shown, respond, shown + respond];
        this.querySender.send(message.join("|"));
    }
}
export default Study;