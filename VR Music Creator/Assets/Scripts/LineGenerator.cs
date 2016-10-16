using UnityEngine;
using System.Collections;
using System.Collections.Generic;

public class LineGenerator : MonoBehaviour {

    public GameObject target;
    public GameObject controller;
    public Material material;
    public float lineWidth;
    public Vector3 a, b;

    private int lineID;
    private bool lineStarted;

    private GameObject currentLine;
    private List<Vector3> points;
    private SteamVR_TrackedController trackedController;
    private LineRenderer lr;


	// Use this for initialization
	void Start () {
        //trackedController = controller.GetComponent<SteamVR_TrackedController>();
	}
	
	// Update is called once per frame
	void Update () {
		if ((Input.GetMouseButtonDown(0) && !lineStarted)) {//|| (trackedController.triggerPressed && !lineStarted)) {
            lineStarted = true;
			GetComponent<SuperCollider> ().StartRecording ();
            StartLine();
        }

		if ((Input.GetMouseButtonDown(1) && lineStarted)) {// || (trackedController.padPressed && lineStarted)) {
            lineStarted = false;
			GetComponent<SuperCollider> ().StopRecording ();
            EndLine();
        }
			
        if(lineStarted)
            AddPoint();

	}

    private void StartLine() {
        // Create line and array of points
        currentLine = CreateLine();
        lr = currentLine.GetComponent<LineRenderer>();
        lr.SetVertexCount(1);

        // Create points array
        points = new List<Vector3>();

        Debug.Log("Line started at " + target.transform.position.ToString());
    }

    private void EndLine() {
        Debug.Log("Line ended at " + target.transform.position.ToString());
    }

    private void AddPoint() {
        points.Add(target.transform.position);
        lr.SetVertexCount(points.Count);
        lr.SetPositions(points.ToArray());
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