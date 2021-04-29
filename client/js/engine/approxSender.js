class ApproximateSender {
    constructor(engine) {
        this.route = "/stream/";
        this.callback = (msg) => {
            console.log("Receiving data");
            const data = JSON.parse(msg.data);
            // $("#answer").html(data);
            engine.render_data = data["data"];
            if (engine.current_time !== data["timestamp"]) {
                engine.current_time = data["timestamp"];
                engine.createApproximation();
            }
            else {
                const plotName = data["name"];
                engine.updatePlotChartJS(engine.render_data, plotName);
            }
        }
    }
}
export default ApproximateSender;