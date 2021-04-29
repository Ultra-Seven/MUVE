class BackSender {
    constructor(engine) {
        this.route = "/stream/";
        this.callback = (msg) => {
            console.log("Receiving data");
            const data = JSON.parse(msg.data);
            // $("#answer").html(data);
            this.render_data = data["data"];
            this.debug_data = data["debug"];
            // engine.append_debug(this.debug_data);
            this.renderTime = 0;
            const template = data["debug"]
            if (this.render_data.length === 0) {
                $("#viz").empty();
                $("#viz_title").html("No Results for " + JSON.stringify(template));
            }
            else {
                engine.drawChartJS(this.render_data);
            }
        }
    }
}
export default BackSender;