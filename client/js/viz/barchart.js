import _ from "underscore";
class Barchart {
    constructor(container, data) {
        this.container = container;
        this.data = data
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
                color: this.colorScales[rank]
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["label"];
        });
        const title = context + " " + groupby;
        // this.chart = new CanvasJS.Chart(this.container, {
        //     animationEnabled: true,
        //     title: {
        //         text: title,
        //         fontFamily: "Calibri, Optima, Candara, Verdana, Geneva, sans-serif",
        //     },
        //     data: [{
        //         type: "column",
        //         dataPoints: render_data
        //     }]
        // });
        // this.chart.render();
        $("#" + this.container).CanvasJSChart({
            animationEnabled: true,
            title: {
                text: title,
                fontFamily: "Calibri, Optima, Candara, Verdana, Geneva, sans-serif",
            },
            data: [{
                type: "column",
                dataPoints: render_data
            }],
            axisX: {
                labelMaxWidth: 100,
                labelAngle: -90,
                interval: 1
            },
        });
    }
}
export default Barchart;