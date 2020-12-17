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
        const key_arr = _.filter(key.split(/[()]+/), element => element !== "");
        const aggTitle = key_arr[0];
        const target = key_arr.length === 1 ?
            key_arr[0] : key_arr[1].replaceAll("_"," ");
        let aggPrefix;
        if (aggTitle.startsWith("max")) {
            aggPrefix = "Maximum of ";
        }
        else if (aggTitle.startsWith("max")) {
            aggPrefix = "Minimum of ";
        }
        else {
            aggPrefix = "Count of ";
        }
        const title = aggPrefix + target + " for " + groupby;
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
            }]
        });
    }
}
export default Barchart;