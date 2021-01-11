import _ from "underscore";
class Barchart {
    constructor(container, engine) {
        this.container = container;
        this.engine = engine;
        this.colorScales = [
            "#ff0000", "#fff200",
            "#00ff00", "#008255", "#5180dd",
            "#0055ff", "#2600ff"
        ];
    }
    drawBarChart(data, groupby) {
        const nrGroups = this.colorScales.length;
        this.data = data;
        const key = Object.keys(data[0]["results"])[0];
        const type = data[0]["type"];
        const context = data[0]["context"];
        let render_data = _.map(this.data, obj => {
            const element = obj["results"][key][0];
            const rank = Math.min(obj["rank"], nrGroups - 1);
            return {
                y: type === "agg" ? parseInt(element) : obj["results"][key].length,
                label: obj["label"],
                color: this.colorScales[rank],
                click: (e) => {
                    this.engine.end = Date.now();
                    alert("Timer stops! Please submit your results");
                },
                mouseover: (e) => {
                    console.log(this.engine.renderTime);
                    if (this.engine.renderTime === 0) {
                        this.engine.renderTime = Date.now();
                    }
                }
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["label"];
        });
        const title = context + " \"" + groupby + "\"";
        const fontColor = context === "value" ? "#8803a9" : "#774a00";

        $("#" + this.container).CanvasJSChart({
            animationEnabled: true,
            title: {
                text: title,
                fontFamily: "Calibri, Optima, Candara, Verdana, Geneva, sans-serif",
                fontColor: fontColor
            },
            data: [{
                type: "column",
                indexLabel: "{y}",
                indexLabelPlacement: "outside",
                indexLabelOrientation: "horizontal",
                dataPoints: render_data
            }],
            axisX: {
                labelMaxWidth: 100,
                labelAngle: 90,
                interval: 1
            },
        });
    }
}
export default Barchart;