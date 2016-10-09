using UnityEngine;
using UnityEditor;

[CustomEditor(typeof(LineGenerator))]
public class LineGeneratorEditor : Editor {
    
    private LineGenerator script;

    public override void OnInspectorGUI() {
        script = (LineGenerator) target;

        DrawDefaultInspector();

        if (GUILayout.Button("Generate"))
            script.GenerateLine();

    }
}
