using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class Controller : MonoBehaviour {

    private SteamVR_TrackedController trackedController;
    public bool isClicked;

	// Use this for initialization
	void Start () {
        trackedController = GetComponent<SteamVR_TrackedController>();
        
	}
	
	// Update is called once per frame
	void Update () {
        isClicked = trackedController.triggerPressed;
    }
}
