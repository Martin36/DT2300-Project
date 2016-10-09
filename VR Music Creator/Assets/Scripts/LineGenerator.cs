using UnityEngine;
using System.Collections;
using System.Collections.Generic;

public class LineGenerator : MonoBehaviour {

    public GameObject target;
    public Material material;
    public float lineWidth;
    public int pointAccuracy;
    public Vector3 a, b;

    private int lineID, dynamicLineID, frames;
    private bool lineStarted;

    private GameObject currentLine;
    private List<Vector3> points;

	// Use this for initialization
	void Start () {
	    
	}
	
	// Update is called once per frame
	void Update () {
        if (Input.GetMouseButtonDown(0) && lineStarted == false) {
            lineStarted = true;
            StartLine();
        }

        if (Input.GetMouseButtonDown(1) && lineStarted) {
            lineStarted = false;
            EndLine();
        }

        if (frames == pointAccuracy) {
            frames = 0;

            if(lineStarted)
                AddPoint();
        }
            
        frames++;

	}

    private void StartLine() {
        // Create line and array of points
        currentLine = CreateLine();
        LineRenderer lr = currentLine.GetComponent<LineRenderer>();

        // Create points array and add start point
        points = new List<Vector3>();
        points.Add(target.transform.position);
        //lr.SetPositions(points.ToArray());

        Debug.Log("Line started at " + target.transform.position.ToString());
    }

    private void EndLine() {
        LineRenderer lr = currentLine.GetComponent<LineRenderer>();

        points.Add(target.transform.position);

        lr.SetVertexCount(points.Count);
        lr.SetPositions(points.ToArray());

        Debug.Log("Line ended at " + target.transform.position.ToString());
    }

    private void AddPoint() {
        points.Add(target.transform.position);

        Debug.Log("Point added at " + target.transform.position.ToString());

    }



    public void GenerateLine() {
        GameObject go = CreateLine();
        LineRenderer line = go.GetComponent<LineRenderer>();

        List<Vector3> points = new List<Vector3>();
        points.Add(a);
        points.Add(b);
        Vector3[] pointsArray = points.ToArray();

        line.SetPositions(pointsArray);

        line.transform.parent = this.transform;
        lineID++;

    }

    GameObject CreateLine() {
        Transform[] children = gameObject.GetComponentsInChildren<Transform>();
        lineID = children.Length;

        GameObject line = new GameObject("Line " + lineID);
        LineRenderer r = line.AddComponent<LineRenderer>();

        r.SetWidth(lineWidth, lineWidth);
        r.sharedMaterial = material;

        line.transform.parent = this.transform;
        lineID++;

        return line;
    }
}
