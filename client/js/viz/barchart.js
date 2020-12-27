import _ from "underscore";
class Barchart {
    constructor(container, data) {
        this.container = container;
        this.data = data
    }
    drawBarChart(data, groupby) {
        this.data = data;
        const key = Object.keys(data[0]["results"])[0];
        const type = data[0]["type"];
        const context = data[0]["context"];
        let render_data = _.map(this.data, obj => {
            const element = obj["results"][key][0];
            return {
                y: type === "agg" ? parseInt(element) : obj["results"][key].length,
                label: obj["label"]
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["label"];
        });
        console.log(render_data);
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