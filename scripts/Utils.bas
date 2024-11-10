Attribute VB_Name = "Utils"
' Copyright (c) 2024 Emlyn Corrin.
' This work is licensed under the terms of the MIT license.
' For a copy, see <https://opensource.org/license/MIT>.

Function BaseName(fName As String) As String
  Dim i, pos As Integer
  
  i = 0
  
  Do
    pos = i + 1
    i = InStr(pos, fName, ".")
  Loop While i > 0
  
  If pos > 0 Then
    BaseName = Left(fName, pos - 2)
  Else
    BaseName = fName
  End If
End Function

Sub Access(path As String)
    Dim n As Integer
    Dim paths
    n = InStr(path, "/fractals/")
    paths = Array(Left(path, n + Len("fractals")))
    GrantAccessToMultipleFiles (paths)
End Sub

Sub ExportImage(pres As Presentation, width As Integer)
  Dim Name As String
  Dim r As Double
  
  Name = BaseName(pres.FullName)
  Access (Name)
  Debug.Print (pres.FullName & " : " & width)
  
  r = pres.PageSetup.SlideWidth / pres.PageSetup.SlideHeight
  
  pres.Slides(1).Export Name & "_" & width & ".png", "PNG", width, Int(width / r + 0.5)
End Sub

Public Sub PNGExport()
  ExportImage ActivePresentation, 2400
  ExportImage ActivePresentation, 1200
  ExportImage ActivePresentation, 600
End Sub

Public Sub PreciseRotation()
    Dim shape As shape
    Dim message As String
    Dim answer

    With ActiveWindow.Selection
        If ActiveWindow.Selection.Type <> ppSelectionShapes Then
            answer = MsgBox("Please select one or more shapes and try again", , "Precise Rotate")
            Exit Sub
        End If
        message = "You have selected " & .ShapeRange.Count & " shape(s):" & vbNewLine
        For Each shape In .ShapeRange
          message = message & "- " & shape.Name & ": " & shape.Rotation & "¡" & vbNewLine
        Next
        answer = InputBox(message, "Pecise Rotation", .ShapeRange(1).Rotation)
        If answer <> "" Then
            For Each shape In .ShapeRange
              shape.Rotation = answer
            Next
        End If
    End With
End Sub

Public Sub PreciseSize()
    Dim shape As shape
    Dim message As String
    Dim i As Integer
    Dim w, h As String
    Dim answer

    With ActiveWindow.Selection
        If ActiveWindow.Selection.Type <> ppSelectionShapes Then
            answer = MsgBox("Please select one or more shapes and try again", , "Precise Width")
            Exit Sub
        End If
        With ActivePresentation.PageSetup
            message = "Page size: " & .SlideWidth & " x " & .SlideHeight & vbNewLine & vbNewLine
        End With
        message = message & "You have selected " & .ShapeRange.Count & " shape(s):" & vbNewLine
        For Each shape In .ShapeRange
            message = message & "- " & shape.Name & ": " & shape.width & " x " & shape.Height & vbNewLine
        Next
        answer = InputBox(message, "Pecise Width", .ShapeRange(1).width & " x " & .ShapeRange(1).Height)
        If answer <> "" Then
            i = InStr(answer, "x")
            w = Trim(Left(answer, i - 1))
            h = Trim(Mid(answer, i + 1))
            Debug.Print ("Using width='" & w & "' and height='" & h & "'")
            For Each shape In .ShapeRange
              shape.width = w
              shape.Height = h
            Next
        End If
    End With
End Sub

