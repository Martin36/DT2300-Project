using UnityEngine;
using System.Collections;
using UnityOSC;
using System.Collections.Generic;

public class SuperCollider : MonoBehaviour {

	// Use this for initialization
	void Start () {
        OSCHandler.Instance.Init();
	}
	
	// Update is called once per frame
	void Update () {
        List<float> vecArray = new List<float>();
        Vector3 vec = transform.position;
        Quaternion q = transform.rotation;

        float x = vec.x;
        float y = vec.y;
        float z = vec.z;

        vecArray.Add(1);
        vecArray.Add(x);
        vecArray.Add(y);
        vecArray.Add(z);
        vecArray.Add(q.w);
        vecArray.Add(q.x);
        vecArray.Add(q.y);
        vecArray.Add(q.z);

        OSCHandler.Instance.SendMessageToClient("SuperCollider", "/RigidBody", vecArray);
    }

	public void StartRecording() {
		OSCHandler.Instance.SendMessageToClient ("SuperCollider", "/Startrec", 1);
	}

	public void StopRecording() {
		OSCHandler.Instance.SendMessageToClient ("SuperCollider", "/Startrec", 0);
	}
}
