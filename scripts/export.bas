Attribute VB_Name = "Export"
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

Public Sub Export(pres As Presentation, width As Integer)
  Dim Name As String
  Dim r As Double
  
  Name = BaseName(pres.FullName)
  Access (Name)
  Debug.Print (pres.FullName & " : " & width)
  
  r = pres.PageSetup.SlideWidth / pres.PageSetup.SlideHeight
  
  pres.Slides(1).Export Name & "_" & width & ".png", "PNG", width, Int(width / r + 0.5)
End Sub

Public Sub Run()
  Export ActivePresentation, 2400
  Export ActivePresentation, 1200
  Export ActivePresentation, 600
End Sub
